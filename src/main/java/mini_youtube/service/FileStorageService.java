package mini_youtube.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import mini_youtube.exception.BusinessException;

@Service
public class FileStorageService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".mp4", ".webm", ".mov", ".mkv", ".avi");

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir:uploads/videos}") String uploadDirProperty) {
        this.uploadDir = Paths.get(uploadDirProperty).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new BusinessException("無法建立影片儲存目錄", e);
        }
    }

    /**
     * 儲存上傳的影片檔案，回傳存放在磁碟上的檔名（不是原始檔名）。
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("請選擇要上傳的影片檔案");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支援的影片格式，僅支援：" + String.join(", ", ALLOWED_EXTENSIONS));
        }

        // 額外檢查瀏覽器/用戶端宣告的 MIME type，作為副檔名檢查以外的第二層防護，
        // 防止有人把非影片檔案改副檔名偽裝成影片上傳。
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("video/")) {
            throw new BusinessException("檔案內容類型不是有效的影片格式");
        }

        // 儲存檔名一律使用系統產生的 UUID，不使用使用者原始檔名，避免路徑跳脫或檔名注入問題。
        String storedFilename = UUID.randomUUID() + extension;

        try {
            Path target = this.uploadDir.resolve(storedFilename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 不要把底層例外訊息（可能包含伺服器檔案路徑）直接回傳給前端。
            throw new BusinessException("影片儲存失敗，請稍後再試", e);
        }

        return storedFilename;
    }

    /**
     * 依儲存檔名解析出實際檔案路徑，並防止路徑跳脫（path traversal）。
     */
    public Path resolve(String storedFilename) {
        Path filePath = this.uploadDir.resolve(storedFilename).normalize();
        if (!filePath.startsWith(this.uploadDir)) {
            throw new BusinessException("非法的檔案路徑");
        }
        if (!Files.exists(filePath)) {
            throw new BusinessException("找不到影片檔案");
        }
        return filePath;
    }

    /**
     * 從磁碟中安全刪除已儲存的影片檔案。
     */
    public void delete(String storedFilename) {
        try {
            Path filePath = this.uploadDir.resolve(storedFilename).normalize();
            if (filePath.startsWith(this.uploadDir)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException e) {
            // 刪除實體檔案失敗時僅記錄，不應完全中斷資料庫刪除流程
        }
    }
}
