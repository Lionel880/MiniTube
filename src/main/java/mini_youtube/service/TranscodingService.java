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

        String newStoredFilename = UUID.randomUUID().toString() + ".mp4";
        Path targetPath = sourcePath.getParent().resolve(newStoredFilename);

        File source = sourcePath.toFile();
        File target = targetPath.toFile();

        try {
            MultimediaObject mediaObject;
            synchronized (lock) {
                mediaObject = new MultimediaObject(source);
            }
            String videoCodec = "";
            if (mediaObject.getInfo().getVideo() != null) {
                videoCodec = mediaObject.getInfo().getVideo().getDecoder();
            }
            String format = mediaObject.getInfo().getFormat();
            log.info("影片詳細資訊: 封裝格式 = {}, 影像編碼 = {}", format, videoCodec);

            // 檢查如果已經是 h264 編碼且封裝是 mp4，則可以直接使用原始檔案，跳過昂貴的轉碼過程
            if ("h264".equalsIgnoreCase(videoCodec) && ("mp4".equalsIgnoreCase(format) || format.toLowerCase().contains("mp4"))) {
                log.info("影片已是標準 H.264 MP4 格式，跳過轉碼直接啟用");
                String coverFilename = generateCover(sourcePath);
                if (coverFilename != null) {
                    video.setCoverUrl(coverFilename);
                }
                video.setStatus(VideoStatus.READY);
                videoRepository.save(video);
                return;
            }

            log.info("正在執行 FFmpeg 轉碼 (目標檔名: {})...", newStoredFilename);
            boolean transcodeSuccess = runTranscodeCommand(sourcePath, targetPath);
            if (!transcodeSuccess) {
                throw new RuntimeException("FFmpeg 轉碼命令行執行失敗");
            }
            log.info("轉碼成功！新檔案大小: {} bytes", target.length());

            // 轉碼成功，更新資料庫資訊
            String coverFilename = generateCover(targetPath);
            if (coverFilename != null) {
                video.setCoverUrl(coverFilename);
            }
            video.setFilePath(newStoredFilename);
            video.setFileSize(target.length());
            video.setStatus(VideoStatus.READY);
            videoRepository.save(video);

            // 刪除原始上傳的檔案 (如果是不同檔案才刪除)
            if (!storedFilename.equals(newStoredFilename)) {
                try {
                    Files.deleteIfExists(sourcePath);
                    log.info("成功清理原始影片檔案: {}", storedFilename);
                } catch (IOException e) {
                    log.warn("清理原始檔案失敗: {}", storedFilename, e);
                }
            }

        } catch (Exception e) {
            log.error("轉碼失敗，影片 ID: {}", videoId, e);
            // 轉碼失敗，清理可能產生到一半的殘留檔案
            if (target.exists()) {
                target.delete();
            }
            updateStatus(video, VideoStatus.FAILED);
        }
    }

    private boolean runTranscodeCommand(Path sourcePath, Path targetPath) {
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
                "-c:a", "aac",
                "-b:a", "128k",
                targetPath.toAbsolutePath().toString()
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 關鍵防卡死：異步讀取並消耗輸出流，避免作業系統的 Pipe Buffer 被塞滿導致進程掛起
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    // 靜默消耗輸出
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

    private String generateCover(Path videoPath) {
        String coverFilename = UUID.randomUUID().toString() + ".jpg";
        Path coverPath = videoPath.getParent().resolve(coverFilename);
        
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
                log.info("影片封面截圖生成成功: {}", coverFilename);
                return coverFilename;
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
        videoRepository.save(video);
    }
}
