package mini_youtube.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import mini_youtube.exception.BusinessException;
import mini_youtube.dto.Request.UpdateVideoRequest;
import mini_youtube.dto.Response.VideoDetailResponse;
import mini_youtube.dto.Response.VideoListResponse;
import mini_youtube.dto.Response.VideoSummaryResponse;
import mini_youtube.entity.Folder;
import mini_youtube.entity.User;
import mini_youtube.entity.Video;
import mini_youtube.entity.VideoStatus;
import mini_youtube.repository.FolderRepository;
import mini_youtube.repository.UserRepository;
import mini_youtube.repository.VideoRepository;

@Service
@RequiredArgsConstructor
public class VideoService {

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 50;

    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final FileStorageService fileStorageService;
    private final TranscodingService transcodingService;

    @Transactional
    public VideoDetailResponse upload(String username, String title, String description, Long folderId, MultipartFile file) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("影片標題不能為空");
        }
        if (title.trim().length() > 200) {
            throw new BusinessException("影片標題不能超過 200 個字");
        }
        if (description != null && description.length() > 2000) {
            throw new BusinessException("影片描述不能超過 2000 個字");
        }

        User uploader = getUserOrThrow(username);
        
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new BusinessException("找不到該資料夾"));
            if (!folder.getOwner().getUsername().equalsIgnoreCase(username)) {
                throw new BusinessException("您沒有權限將影片放入該資料夾");
            }
        }

        String folderName = folder != null ? folder.getName() : null;
        String storedFilename = fileStorageService.store(folderName, file);

        String finalTitle = title.trim();
        if (finalTitle.length() > 190) {
            finalTitle = finalTitle.substring(0, 190);
        }

        Video video = Video.builder()
                .title(finalTitle)
                .description(description == null ? "" : description.trim())
                .filePath(storedFilename)
                .fileSize(file.getSize())
                .status(VideoStatus.UPLOADING)
                .viewCount(0L)
                .uploader(uploader)
                .folder(folder)
                .build();

        Video saved = videoRepository.save(video);
        
        // 觸發非同步背景轉碼 (待交易提交後，以確保異步執行緒能查到影片)
        triggerTranscodingAsync(saved.getId(), storedFilename);
        
        return toDetailResponse(saved, username);
    }

    @Transactional
    public VideoDetailResponse uploadChunk(String username, String uploadId, int chunkIndex, int totalChunks,
                                           String title, String description, Long folderId, Long videoId, MultipartFile file) {
        User uploader = getUserOrThrow(username);
        
        fileStorageService.storeChunk(uploadId, chunkIndex, file);

        Video video;
        if (videoId == null) {
            Folder folder = null;
            if (folderId != null) {
                folder = folderRepository.findById(folderId)
                        .orElseThrow(() -> new BusinessException("找不到該資料夾"));
                if (!folder.getOwner().getUsername().equalsIgnoreCase(username)) {
                    throw new BusinessException("您沒有權限將影片放入該資料夾");
                }
            }

            String finalTitle = title.trim();
            if (finalTitle.length() > 190) {
                finalTitle = finalTitle.substring(0, 190);
            }

            video = Video.builder()
                    .title(finalTitle)
                    .description(description == null ? "" : description.trim())
                    .filePath("temp_placeholder/" + uploadId)
                    .fileSize(0L)
                    .status(VideoStatus.UPLOADING)
                    .viewCount(0L)
                    .uploader(uploader)
                    .folder(folder)
                    .build();

            video = videoRepository.save(video);
        } else {
            video = videoRepository.findById(videoId)
                    .orElseThrow(() -> new BusinessException("找不到影片資訊"));
            if (!video.getUploader().getUsername().equalsIgnoreCase(username)) {
                throw new BusinessException("您沒有權限修改此影片");
            }
        }

        if (fileStorageService.isAllChunksUploaded(uploadId, totalChunks)) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null && originalFilename.equals("blob") && title.contains(".")) {
                originalFilename = title;
            } else if (originalFilename == null || originalFilename.equals("blob")) {
                originalFilename = title + ".mp4";
            }
            String folderName = video.getFolder() != null ? video.getFolder().getName() : null;
            
            triggerMergeAndTranscodingAsync(video.getId(), uploadId, totalChunks, folderName, originalFilename);
        }

        return toDetailResponse(video, username);
    }

    private void triggerMergeAndTranscodingAsync(Long videoId, String uploadId, int totalChunks, String folderName, String originalFilename) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        transcodingService.mergeAndTranscode(videoId, uploadId, totalChunks, folderName, originalFilename);
                    }
                }
            );
        } else {
            transcodingService.mergeAndTranscode(videoId, uploadId, totalChunks, folderName, originalFilename);
        }
    }

    private void triggerTranscodingAsync(Long videoId, String storedFilename) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        transcodingService.transcodeToMp4(videoId, storedFilename);
                    }
                }
            );
        } else {
            transcodingService.transcodeToMp4(videoId, storedFilename);
        }
    }

    @Transactional
    public List<VideoDetailResponse> uploadBatch(String username, Long folderId, MultipartFile[] files) {
        User uploader = getUserOrThrow(username);
        
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new BusinessException("找不到該資料夾"));
            if (!folder.getOwner().getUsername().equalsIgnoreCase(username)) {
                throw new BusinessException("您沒有權限將影片放入該資料夾");
            }
        }

        List<VideoDetailResponse> responses = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }

            String originalFilename = file.getOriginalFilename();
            String title = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(0, originalFilename.lastIndexOf('.'))
                    : (originalFilename != null ? originalFilename : "未命名影片");

            String folderName = folder != null ? folder.getName() : null;
            String storedFilename = fileStorageService.store(folderName, file);

            String finalTitle = title.trim();
            if (finalTitle.length() > 190) {
                finalTitle = finalTitle.substring(0, 190);
            }

            Video video = Video.builder()
                    .title(finalTitle)
                    .description("")
                    .filePath(storedFilename)
                    .fileSize(file.getSize())
                    .status(VideoStatus.UPLOADING)
                    .viewCount(0L)
                    .uploader(uploader)
                    .folder(folder)
                    .build();

            Video saved = videoRepository.save(video);
            
            // 觸發非同步背景轉碼 (待交易提交後)
            triggerTranscodingAsync(saved.getId(), storedFilename);
            
            responses.add(toDetailResponse(saved, username));
        }
        return responses;
    }

    /** 影片跟著使用者走：列表只回傳目前登入者自己上傳的影片，支援資料夾過濾。 */
    @Transactional(readOnly = true)
    public VideoListResponse list(String username, String folderId, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), getSort(sortBy, sortDir));
        Page<Video> result;
        if ("root".equalsIgnoreCase(folderId)) {
            result = videoRepository.findByUploaderUsernameAndFolderIsNull(username, pageable);
        } else if (folderId != null && !folderId.isBlank()) {
            try {
                result = videoRepository.findByUploaderUsernameAndFolderId(username, Long.parseLong(folderId), pageable);
            } catch (NumberFormatException e) {
                result = videoRepository.findByUploaderUsername(username, pageable);
            }
        } else {
            result = videoRepository.findByUploaderUsername(username, pageable);
        }
        return toListResponse(result);
    }

    /** 搜尋範圍同樣限定在目前登入者自己的影片，支援資料夾過濾。 */
    @Transactional(readOnly = true)
    public VideoListResponse search(String username, String folderId, String keyword, int page, int size, String sortBy, String sortDir) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        if (safeKeyword.length() > 200) {
            safeKeyword = safeKeyword.substring(0, 200);
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), getSort(sortBy, sortDir));
        Page<Video> result;
        if ("root".equalsIgnoreCase(folderId)) {
            result = videoRepository.searchByUploaderAndFolderIsNull(username, safeKeyword, pageable);
        } else if (folderId != null && !folderId.isBlank()) {
            try {
                result = videoRepository.searchByUploaderAndFolder(username, safeKeyword, Long.parseLong(folderId), pageable);
            } catch (NumberFormatException e) {
                result = videoRepository.searchByUploader(username, safeKeyword, pageable);
            }
        } else {
            result = videoRepository.searchByUploader(username, safeKeyword, pageable);
        }
        return toListResponse(result);
    }

    @Transactional
    public VideoDetailResponse update(Long id, String username, UpdateVideoRequest request) {
        Video video = getVideoOrThrow(id);
        User user = getUserOrThrow(username);

        if (!video.getUploader().getId().equals(user.getId())) {
            throw new BusinessException("您無權編輯其他人上傳的影片");
        }

        if (video.getFilePath() != null && !video.getFilePath().isBlank() 
                && !video.getTitle().equalsIgnoreCase(request.getTitle().trim())) {
            String newPath = fileStorageService.renameFile(video.getFilePath(), request.getTitle().trim());
            video.setFilePath(newPath);
        }

        video.setTitle(request.getTitle().trim());
        video.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());

        Video saved = videoRepository.save(video);
        return toDetailResponse(saved, username);
    }

    @Transactional
    public VideoDetailResponse getById(Long id, String currentUsername) {
        Video video = getVideoOrThrow(id);
        video.setViewCount(video.getViewCount() + 1);
        videoRepository.save(video);
        return toDetailResponse(video, currentUsername);
    }

    @Transactional
    public void batchDelete(List<Long> ids, String username) {
        User user = getUserOrThrow(username);
        List<Video> videos = videoRepository.findAllById(ids);

        for (Video video : videos) {
            if (!video.getUploader().getId().equals(user.getId())) {
                throw new BusinessException("您無權刪除其他人上傳的影片");
            }
            fileStorageService.delete(video.getFilePath());
            // 安全清理本機的實體封面圖片檔
            if (video.getCoverUrl() != null && !video.getCoverUrl().isBlank()) {
                fileStorageService.delete(video.getCoverUrl());
            }
            videoRepository.delete(video);
        }
    }

    @Transactional
    public void deleteAll(String username) {
        User user = getUserOrThrow(username);
        List<Video> videos = videoRepository.findByUploader(user);
        for (Video video : videos) {
            fileStorageService.delete(video.getFilePath());
            if (video.getCoverUrl() != null && !video.getCoverUrl().isBlank()) {
                fileStorageService.delete(video.getCoverUrl());
            }
        }
        videoRepository.deleteAll(videos);
    }

    @Transactional(readOnly = true)
    public Path resolveVideoFilePath(Long id) {
        Video video = getVideoOrThrow(id);
        return fileStorageService.resolve(video.getFilePath());
    }

    @Transactional(readOnly = true)
    public Path resolveVideoCoverPath(Long id) {
        Video video = getVideoOrThrow(id);
        if (video.getCoverUrl() == null || video.getCoverUrl().isBlank()) {
            throw new BusinessException("該影片尚無封面圖片");
        }
        return fileStorageService.resolve(video.getCoverUrl());
    }

    @Transactional
    public VideoDetailResponse moveVideoToFolder(Long id, String username, Long folderId) {
        Video video = getVideoOrThrow(id);
        User user = getUserOrThrow(username);

        if (!video.getUploader().getId().equals(user.getId())) {
            throw new BusinessException("您無權編輯此影片");
        }

        String targetFolderName = null;
        if (folderId != null) {
            Folder folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new BusinessException("資料夾不存在"));
            if (!folder.getOwner().getId().equals(user.getId())) {
                throw new BusinessException("您無權限將影片放入此資料夾");
            }
            video.setFolder(folder);
            targetFolderName = folder.getName();
        } else {
            video.setFolder(null);
        }

        if (video.getFilePath() != null && !video.getFilePath().isBlank()) {
            String newPath = fileStorageService.moveFile(video.getFilePath(), targetFolderName);
            video.setFilePath(newPath);
        }

        Video saved = videoRepository.save(video);
        return toDetailResponse(saved, username);
    }

    @Transactional
    public void batchMoveVideosToFolder(List<Long> ids, String username, Long folderId) {
        if (ids == null || ids.isEmpty()) return;
        User user = getUserOrThrow(username);
        Folder folder = null;
        String targetFolderName = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new BusinessException("資料夾不存在"));
            if (!folder.getOwner().getId().equals(user.getId())) {
                throw new BusinessException("您無權限將影片放入此資料夾");
            }
            targetFolderName = folder.getName();
        }

        List<Video> videos = videoRepository.findAllById(ids);
        for (Video video : videos) {
            if (!video.getUploader().getId().equals(user.getId())) {
                throw new BusinessException("您無權編輯此影片");
            }
            video.setFolder(folder);
            if (video.getFilePath() != null && !video.getFilePath().isBlank()) {
                String newPath = fileStorageService.moveFile(video.getFilePath(), targetFolderName);
                video.setFilePath(newPath);
            }
            videoRepository.save(video);
        }
    }

    private Video getVideoOrThrow(Long id) {
        return videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("找不到影片"));
    }

    private User getUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("使用者不存在"));
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Sort getSort(String sortBy, String sortDir) {
        String field = "createdAt";
        if ("title".equalsIgnoreCase(sortBy)) {
            field = "title";
        } else if ("fileSize".equalsIgnoreCase(sortBy)) {
            field = "fileSize";
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private VideoListResponse toListResponse(Page<Video> result) {
        List<VideoSummaryResponse> videos = result.getContent().stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());

        return VideoListResponse.builder()
                .videos(videos)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .page(result.getNumber())
                .size(result.getSize())
                .build();
    }

    private VideoSummaryResponse toSummaryResponse(Video video) {
        return VideoSummaryResponse.builder()
                .id(video.getId())
                .title(video.getTitle())
                .coverUrl(video.getCoverUrl() == null ? null : "/api/videos/" + video.getId() + "/cover")
                .uploaderUsername(video.getUploader().getUsername())
                .viewCount(video.getViewCount())
                .fileSize(safeFileSize(video))
                .status(video.getStatus())
                .createdAt(video.getCreatedAt())
                .folderId(video.getFolder() == null ? null : video.getFolder().getId())
                .folderName(video.getFolder() == null ? null : video.getFolder().getName())
                .transcodeProgress(video.getTranscodeProgress())
                .build();
     }

     /** 舊資料的 fileSize 可能是 NULL，直接 unboxing 會 NPE，一律以 0 代替。 */
     private long safeFileSize(Video video) {
         return video.getFileSize() == null ? 0L : video.getFileSize();
     }

     private VideoDetailResponse toDetailResponse(Video video, String currentUsername) {
         return VideoDetailResponse.builder()
                 .id(video.getId())
                 .title(video.getTitle())
                 .description(video.getDescription())
                 .videoUrl("/api/videos/" + video.getId() + "/stream/video.mp4")
                 .coverUrl(video.getCoverUrl() == null ? null : "/api/videos/" + video.getId() + "/cover")
                 .uploaderUsername(video.getUploader().getUsername())
                 .viewCount(video.getViewCount())
                 .fileSize(safeFileSize(video))
                 .status(video.getStatus())
                 .createdAt(video.getCreatedAt())
                 .folderId(video.getFolder() == null ? null : video.getFolder().getId())
                 .folderName(video.getFolder() == null ? null : video.getFolder().getName())
                 .transcodeProgress(video.getTranscodeProgress())
                 .build();
     }
}
