package mini_youtube.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mini_youtube.dto.Request.UpdateVideoRequest;
import mini_youtube.dto.Response.VideoDetailResponse;
import mini_youtube.dto.Response.VideoListResponse;
import mini_youtube.service.VideoService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/videos")
public class VideoController {

    private static final String ANONYMOUS = "anonymousUser";
    private static final long DEFAULT_CHUNK_SIZE = 1024 * 1024L; // 1MB，沒有帶 Range 時的預設回傳區塊大小

    private final VideoService videoService;

    @PostMapping(value = "/upload")
    public VideoDetailResponse upload(
            Authentication authentication,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestPart("file") MultipartFile file) {
        return videoService.upload(authentication.getName(), title, description, file);
    }

    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<VideoDetailResponse> uploadBatch(
            Authentication authentication,
            @RequestParam("files") MultipartFile[] files) {
        return videoService.uploadBatch(authentication.getName(), files);
    }

    @GetMapping
    public VideoListResponse list(
            Authentication authentication,
            @RequestParam(value = "folderId", required = false) String folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return videoService.list(authentication.getName(), folderId, page, size, sortBy, sortDir);
    }

    @GetMapping("/search")
    public VideoListResponse search(
            Authentication authentication,
            @RequestParam("q") String keyword,
            @RequestParam(value = "folderId", required = false) String folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return videoService.search(authentication.getName(), folderId, keyword, page, size, sortBy, sortDir);
    }

    @PutMapping("/{id}")
    public VideoDetailResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVideoRequest request,
            Authentication authentication) {
        return videoService.update(id, authentication.getName(), request);
    }

    @PutMapping("/{id}/folder")
    public VideoDetailResponse moveVideoToFolder(
            @PathVariable Long id,
            @RequestParam(value = "folderId", required = false) Long folderId,
            Authentication authentication) {
        return videoService.moveVideoToFolder(id, authentication.getName(), folderId);
    }

    @PutMapping("/batch/folder")
    public ResponseEntity<Void> batchMoveVideosToFolder(
            @RequestBody List<Long> ids,
            @RequestParam(value = "folderId", required = false) Long folderId,
            Authentication authentication) {
        videoService.batchMoveVideosToFolder(ids, authentication.getName(), folderId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public VideoDetailResponse getById(@PathVariable Long id, Authentication authentication) {
        return videoService.getById(id, currentUsername(authentication));
    }

    @DeleteMapping("/batch")
    public ResponseEntity<Void> batchDelete(
            @RequestBody List<Long> ids,
            Authentication authentication) {
        videoService.batchDelete(ids, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/all")
    public ResponseEntity<Void> deleteAll(Authentication authentication) {
        videoService.deleteAll(authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/cover")
    public ResponseEntity<UrlResource> getCover(@PathVariable Long id) throws IOException {
        Path filePath = videoService.resolveVideoCoverPath(id);
        UrlResource resource = new UrlResource(filePath.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    /**
     * 影片串流端點，支援 HTTP Range 請求並正確回傳 206 Partial Content。
     *
     * 注意：單純把 controller 回傳型別宣告成 Resource/UrlResource 並不會讓 Spring
     * 自動處理 Range，Spring 只有在回傳型別是 ResourceRegion 時才會啟用
     * ResourceRegionHttpMessageConverter 去解析用戶端的 Range header。
     * 沒有真正的 206，iOS Safari 等對 Range 要求嚴格的用戶端會直接判定影片不可播放；
     * 桌面瀏覽器則是被迫把整支影片下載完才能播，大檔案體驗上就是「點進去播不動」。
     */
    @GetMapping("/{id}/stream/video.mp4")
    public ResponseEntity<ResourceRegion> stream(
            @PathVariable Long id,
            @RequestHeader HttpHeaders headers) throws IOException {

        Path filePath = videoService.resolveVideoFilePath(id);
        UrlResource videoResource = new UrlResource(filePath.toUri());

        if (!videoResource.exists() || !videoResource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        long contentLength = videoResource.contentLength();
        List<HttpRange> ranges = headers.getRange();

        ResourceRegion region;
        HttpStatus status;

        if (ranges.isEmpty()) {
            long rangeLength = Math.min(DEFAULT_CHUNK_SIZE, contentLength);
            region = new ResourceRegion(videoResource, 0, rangeLength);
            status = HttpStatus.OK;
        } else {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(DEFAULT_CHUNK_SIZE, end - start + 1);
            region = new ResourceRegion(videoResource, start, rangeLength);
            status = HttpStatus.PARTIAL_CONTENT;
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(videoResource)
                .orElse(MediaType.valueOf("video/mp4"));

        return ResponseEntity.status(status)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    private String currentUsername(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || ANONYMOUS.equals(authentication.getName())) {
            return null;
        }
        return authentication.getName();
    }
}
