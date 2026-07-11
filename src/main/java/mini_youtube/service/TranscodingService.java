package mini_youtube.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import mini_youtube.entity.Video;
import mini_youtube.entity.VideoStatus;
import mini_youtube.repository.VideoRepository;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;

@Service
@RequiredArgsConstructor
public class TranscodingService {

    private static final Logger log = LoggerFactory.getLogger(TranscodingService.class);

    private final VideoRepository videoRepository;
    private final FileStorageService fileStorageService;
    private static final Object lock = new Object();
    private static final java.util.concurrent.Semaphore transcodeSemaphore = new java.util.concurrent.Semaphore(1);

    /**
     * 應用程式啟動時就記錄 ffmpeg 實際解析到的路徑，而不是等使用者上傳影片失敗才發現。
     * 如果這裡就拋錯或印出的路徑檔案不存在，代表轉碼從一開始就不會成功，
     * 影片會一直卡在 UPLOADING 狀態；這是目前最快排查「卡轉碼／沒有封面」問題的方式。
     */
    @PostConstruct
    public void logFfmpegLocation() {
        try {
            ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator locator =
                    new ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator();
            String ffmpegPath = locator.getExecutablePath();
            boolean exists = new File(ffmpegPath).exists();
            log.info("FFmpeg 執行檔路徑：{}（檔案存在：{}）", ffmpegPath, exists);
            if (!exists) {
                log.warn("FFmpeg 執行檔路徑回報不存在，轉碼功能很可能無法運作，請確認 jave-all-deps 是否支援目前的作業系統/架構。");
            }
        } catch (Exception e) {
            log.error("啟動時解析 FFmpeg 路徑失敗，轉碼功能可能完全無法運作", e);
        }
    }

    @Async
    @Transactional
    public void transcodeToMp4(Long videoId, String storedFilename) {
        log.info("開始背景轉碼影片 ID: {}, 檔案: {}", videoId, storedFilename);

        try {
            // 獲取轉碼信號量，同一時間只允許 1 個影片進行重度轉碼工作以保持系統穩定
            transcodeSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("轉碼任務排隊被中斷: {}", videoId, e);
            return;
        }

        try {
            Video video = videoRepository.findById(videoId).orElse(null);
            if (video == null) {
                log.error("找不到影片 ID: {}, 取消轉碼", videoId);
                return;
            }

            Path sourcePath;
            try {
                sourcePath = fileStorageService.resolve(storedFilename);
            } catch (Exception e) {
                log.error("無法解析影片實體路徑: {}", storedFilename, e);
                updateStatus(video, VideoStatus.FAILED);
                return;
            }

            String originalFilename = sourcePath.getFileName().toString();
            String baseName = originalFilename;
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex >= 0) {
                baseName = originalFilename.substring(0, dotIndex);
            }

            String tempFilename = UUID.randomUUID().toString() + "_temp.mp4";
            Path targetPath = sourcePath.getParent().resolve(tempFilename);

            File source = sourcePath.toFile();
            File target = targetPath.toFile();

            try {
                MultimediaObject mediaObject;
                synchronized (lock) {
                    mediaObject = new MultimediaObject(source);
                }
                long durationMs = mediaObject.getInfo().getDuration();
                String videoCodec = "";
                if (mediaObject.getInfo().getVideo() != null) {
                    videoCodec = mediaObject.getInfo().getVideo().getDecoder();
                }
                String format = mediaObject.getInfo().getFormat();
                log.info("影片詳細資訊: 封裝格式 = {}, 影像編碼 = {}, 總長度 = {} ms", format, videoCodec, durationMs);

                // 檢查如果已經是 h264 編碼且封裝是 mp4，則可以直接進行無損複製並套用 faststart 最佳化，跳過耗時的影像重編碼
                if ("h264".equalsIgnoreCase(videoCodec) && ("mp4".equalsIgnoreCase(format) || format.toLowerCase().contains("mp4"))) {
                    log.info("影片已是標準 H.264 MP4 格式，跳過完全轉碼，改用超快速無損串流複製並套用 faststart 最佳化...");
                    
                    boolean faststartSuccess = runFaststartFix(sourcePath, targetPath);
                    if (faststartSuccess && target.exists() && target.length() > 0) {
                        log.info("無損串流複製成功！新檔案大小: {} bytes", target.length());
                        
                        // 刪除原始上傳的檔案
                        safeDeleteSourceFile(sourcePath, storedFilename);
                        
                        // 重新命名為最終名稱
                        Path parentDir = targetPath.getParent();
                        String finalFilenameOnly = baseName + ".mp4";
                        Path finalPath = parentDir.resolve(finalFilenameOnly);
                        int count = 1;
                        while (Files.exists(finalPath)) {
                            finalFilenameOnly = baseName + "_" + count + ".mp4";
                            finalPath = parentDir.resolve(finalFilenameOnly);
                            count++;
                        }
                        System.gc();
                        Files.move(targetPath, finalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                        String cleanFinalRelativePath;
                        if (storedFilename.contains("/")) {
                            String folderPrefix = storedFilename.substring(0, storedFilename.lastIndexOf("/") + 1);
                            cleanFinalRelativePath = folderPrefix + finalFilenameOnly;
                        } else {
                            cleanFinalRelativePath = finalFilenameOnly;
                        }

                        String coverFilename = generateCover(finalPath);
                        if (coverFilename != null) {
                            video.setCoverUrl(coverFilename);
                        }
                        video.setFilePath(cleanFinalRelativePath);
                        video.setFileSize(finalPath.toFile().length());
                        video.setStatus(VideoStatus.READY);
                        video.setTranscodeProgress(100);
                        videoRepository.save(video);
                    } else {
                        log.warn("無損複製失敗或檔案無效，退回至傳統的直接啟用模式");
                        
                        Path parentDir = sourcePath.getParent();
                        String finalFilenameOnly = baseName + ".mp4";
                        Path finalPath = parentDir.resolve(finalFilenameOnly);
                        int count = 1;
                        while (Files.exists(finalPath)) {
                            finalFilenameOnly = baseName + "_" + count + ".mp4";
                            finalPath = parentDir.resolve(finalFilenameOnly);
                            count++;
                        }
                        System.gc();
                        Files.move(sourcePath, finalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                        String cleanFinalRelativePath;
                        if (storedFilename.contains("/")) {
                            String folderPrefix = storedFilename.substring(0, storedFilename.lastIndexOf("/") + 1);
                            cleanFinalRelativePath = folderPrefix + finalFilenameOnly;
                        } else {
                            cleanFinalRelativePath = finalFilenameOnly;
                        }

                        String coverFilename = generateCover(finalPath);
                        if (coverFilename != null) {
                            video.setCoverUrl(coverFilename);
                        }
                        video.setFilePath(cleanFinalRelativePath);
                        video.setFileSize(finalPath.toFile().length());
                        video.setStatus(VideoStatus.READY);
                        video.setTranscodeProgress(100);
                        videoRepository.save(video);

                        if (target.exists()) {
                            target.delete();
                        }
                    }
                    return;
                }

                log.info("正在執行 FFmpeg 轉碼 (目標臨時檔名: {})...", tempFilename);
                boolean transcodeSuccess = runTranscodeCommand(sourcePath, targetPath, videoId, durationMs);
                if (!transcodeSuccess) {
                    throw new RuntimeException("FFmpeg 轉碼命令行執行失敗");
                }
                log.info("轉碼成功！臨時檔案大小: {} bytes", target.length());

                // 1. 刪除原始上傳的檔案 (sourcePath)
                safeDeleteSourceFile(sourcePath, storedFilename);

                // 2. 重新命名為最終名稱
                Path parentDir = targetPath.getParent();
                String finalFilenameOnly = baseName + ".mp4";
                Path finalPath = parentDir.resolve(finalFilenameOnly);
                int count = 1;
                while (Files.exists(finalPath)) {
                    finalFilenameOnly = baseName + "_" + count + ".mp4";
                    finalPath = parentDir.resolve(finalFilenameOnly);
                    count++;
                }
                System.gc();
                Files.move(targetPath, finalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                String cleanFinalRelativePath;
                if (storedFilename.contains("/")) {
                    String folderPrefix = storedFilename.substring(0, storedFilename.lastIndexOf("/") + 1);
                    cleanFinalRelativePath = folderPrefix + finalFilenameOnly;
                } else {
                    cleanFinalRelativePath = finalFilenameOnly;
                }

                // 轉碼成功，更新資料庫資訊
                String coverFilename = generateCover(finalPath);
                if (coverFilename != null) {
                    video.setCoverUrl(coverFilename);
                }
                video.setFilePath(cleanFinalRelativePath);
                video.setFileSize(finalPath.toFile().length());
                video.setStatus(VideoStatus.READY);
                video.setTranscodeProgress(100);
                videoRepository.save(video);

            } catch (Exception e) {
                log.error("轉碼失敗，影片 ID: {}", videoId, e);
                // 轉碼失敗，清理可能產生到一半的殘留檔案
                if (target.exists()) {
                    target.delete();
                }
                updateStatus(video, VideoStatus.FAILED);
            }
        } finally {
            // 釋放信號量，讓下一個排隊的轉碼任務可以執行
            transcodeSemaphore.release();
        }
    }

    private boolean runTranscodeCommand(Path sourcePath, Path targetPath, Long videoId, long durationMs) {
        try {
            ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator locator = new ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator();
            String ffmpegPath = locator.getExecutablePath();
            
            ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-y",
                "-i", sourcePath.toAbsolutePath().toString(),
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-profile:v", "high",
                "-level", "3.1",
                "-movflags", "faststart",
                "-c:a", "aac",
                "-b:a", "128k",
                "-progress", "-",
                targetPath.toAbsolutePath().toString()
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            int lastProgress = 0;
            
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("out_time_us=")) {
                        try {
                            long currentUs = Long.parseLong(line.substring(12).trim());
                            long currentMs = currentUs / 1000;
                            if (durationMs > 0 && currentMs > 0) {
                                int progress = (int) ((currentMs * 100) / durationMs);
                                progress = Math.min(progress, 99);
                                if (progress > lastProgress) {
                                    lastProgress = progress;
                                    updateTranscodeProgress(videoId, progress);
                                }
                            }
                        } catch (Exception e) {
                            // 忽略
                        }
                    }
                }
            }
            
            int exitCode = process.waitFor();
            log.info("FFmpeg 轉碼進程退出，退出碼: {}", exitCode);
            return exitCode == 0;
        } catch (Exception e) {
            log.error("執行影片轉碼命令發生異常", e);
        }
        return false;
    }

    private long parseTimeStrToMs(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            if (parts.length == 3) {
                double hours = Double.parseDouble(parts[0]);
                double minutes = Double.parseDouble(parts[1]);
                double seconds = Double.parseDouble(parts[2]);
                return (long) ((hours * 3600 + minutes * 60 + seconds) * 1000);
            }
        } catch (Exception e) {
            // 忽略
        }
        return -1;
    }

    private void updateTranscodeProgress(Long videoId, int progress) {
        try {
            Video video = videoRepository.findById(videoId).orElse(null);
            if (video != null) {
                video.setTranscodeProgress(progress);
                videoRepository.save(video);
            }
        } catch (Exception e) {
            log.error("更新轉碼進度失敗, ID: {}, progress: {}", videoId, progress, e);
        }
    }

    private void safeDeleteSourceFile(Path path, String storedFilename) {
        // 先進行垃圾回收，協助 JVM 釋放對實體檔案的控制鎖（尤其是 Jave MultimediaObject 產生的鎖）
        System.gc();
        
        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            try {
                if (Files.deleteIfExists(path)) {
                    log.info("成功清理原始影片檔案: {}", storedFilename);
                    return;
                } else {
                    log.info("原始影片檔案不存在或已被清理: {}", storedFilename);
                    return;
                }
            } catch (IOException e) {
                log.warn("清理原始檔案失敗 (嘗試第 {}/{} 次): {}", (i + 1), maxRetries, e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                System.gc(); // 再次嘗試回收
            }
        }
        log.error("無法清理原始影片檔案，已達最大重試次數: {}", storedFilename);
    }

    private String generateCover(Path videoPath) {
        String coverFilename = UUID.randomUUID().toString() + ".jpg";
        Path coverPath = fileStorageService.getCoversDir().resolve(coverFilename);
        
        try {
            String ffmpegPath;
            synchronized (lock) {
                ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator locator = new ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator();
                ffmpegPath = locator.getExecutablePath();
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-y",
                "-ss", "00:00:01",
                "-i", videoPath.toAbsolutePath().toString(),
                "-vframes", "1",
                "-f", "mjpeg",
                coverPath.toAbsolutePath().toString()
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 消耗輸出流，防止管道阻塞
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    // 靜默消耗
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                String dbCoverPath = "covers/" + coverFilename;
                log.info("影片封面截圖生成成功: {}", dbCoverPath);
                return dbCoverPath;
            } else {
                log.error("影片封面截圖生成失敗，FFmpeg 退出碼: {}", exitCode);
            }
        } catch (Exception e) {
            log.error("生成影片封面截圖發生異常", e);
        }
        return null;
    }

    private void updateStatus(Video video, VideoStatus status) {
        video.setStatus(status);
        if (status == VideoStatus.READY) {
            video.setTranscodeProgress(100);
        } else if (status == VideoStatus.FAILED) {
            video.setTranscodeProgress(0);
        }
        videoRepository.save(video);
    }

    @PostConstruct
    public void fixExistingVideos() {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("開始自動修復所有現有影片的 Faststart 格式...");
                for (Video video : videoRepository.findAll()) {
                    if (video.getStatus() == VideoStatus.READY && video.getFilePath() != null) {
                        try {
                            Path filePath = fileStorageService.resolve(video.getFilePath());
                            Path tempPath = filePath.getParent().resolve(video.getFilePath() + "_temp.mp4");
                            
                            log.info("正在檢測/修復影片 ID: {}, 路徑: {}", video.getId(), filePath);
                            boolean success = runFaststartFix(filePath, tempPath);
                            if (success && Files.exists(tempPath) && Files.size(tempPath) > 0) {
                                Files.move(tempPath, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                log.info("影片 ID: {} 修復 Faststart 成功！", video.getId());
                            } else {
                                log.info("影片 ID: {} 不需要修復或修復跳過", video.getId());
                                Files.deleteIfExists(tempPath);
                            }
                        } catch (Exception ex) {
                            log.error("修復影片 ID: {} 發生錯誤", video.getId(), ex);
                        }
                    }
                }
                log.info("現有影片 Faststart 修復流程結束。");
            } catch (Exception e) {
                log.error("修復任務啟動失敗", e);
            }
        }).start();
    }

    private boolean runFaststartFix(Path sourcePath, Path targetPath) {
        try {
            String ffmpegPath;
            synchronized (lock) {
                ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator locator = new ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator();
                ffmpegPath = locator.getExecutablePath();
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-y",
                "-i", sourcePath.toAbsolutePath().toString(),
                "-c", "copy",
                "-movflags", "faststart",
                targetPath.toAbsolutePath().toString()
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {}
            }
            
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.error("執行 faststart 修復命令失敗", e);
        }
        return false;
    }
}
