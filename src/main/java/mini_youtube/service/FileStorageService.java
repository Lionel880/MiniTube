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

    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            ".mp4", ".webm", ".mov", ".mkv", ".avi",
            ".flv", ".3gp", ".wmv", ".m4v", ".mpg", ".mpeg"
    );

    private final Path uploadDir;
    private final Path coversDir;

    public FileStorageService(@Value("${app.upload-dir:uploads/videos}") String uploadDirProperty) {
        this.uploadDir = Paths.get(uploadDirProperty).toAbsolutePath().normalize();
        this.coversDir = this.uploadDir.resolve("covers").normalize();
        try {
            Files.createDirectories(this.uploadDir);
            Files.createDirectories(this.coversDir);
        } catch (IOException e) {
            throw new BusinessException("無法建立影片儲存目錄或封面目錄", e);
        }
    }

    public Path getCoversDir() {
        return this.coversDir;
    }

    public String store(MultipartFile file) {
        return store(null, file);
    }

    public String store(String folderName, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("請選擇要上傳的影片檔案");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());

        if (originalFilename.isBlank()) {
            originalFilename = "unnamed_video.mp4";
        }

        String extension = "";
        String baseName = originalFilename;
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
            baseName = originalFilename.substring(0, dotIndex);
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支援的影片格式，僅支援：" + String.join(", ", ALLOWED_EXTENSIONS));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("video/")) {
            throw new BusinessException("檔案內容類型不是有效的影片格式");
        }

        Path targetDir = this.uploadDir;
        if (folderName != null && !folderName.isBlank()) {
            String cleanFolderName = StringUtils.cleanPath(folderName).replace("/", "").replace("\\", "");
            targetDir = this.uploadDir.resolve(cleanFolderName);
        }

        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new BusinessException("無法建立影片儲存目錄", e);
        }

        String storedFilename = originalFilename;
        Path target = targetDir.resolve(storedFilename);
        int count = 1;
        while (Files.exists(target)) {
            storedFilename = baseName + "_" + count + extension;
            target = targetDir.resolve(storedFilename);
            count++;
        }

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("影片儲存失敗，請稍後再試", e);
        }

        if (folderName != null && !folderName.isBlank()) {
            String cleanFolderName = StringUtils.cleanPath(folderName).replace("/", "").replace("\\", "");
            return cleanFolderName + "/" + storedFilename;
        } else {
            return storedFilename;
        }
    }

    public String renameFile(String oldRelativePath, String newTitle) {
        try {
            Path oldPath = this.uploadDir.resolve(oldRelativePath).normalize();
            if (!oldPath.startsWith(this.uploadDir) || !Files.exists(oldPath)) {
                return oldRelativePath;
            }

            String filename = oldPath.getFileName().toString();
            String extension = "";
            int dotIndex = filename.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = filename.substring(dotIndex);
            }

            String cleanTitle = StringUtils.cleanPath(newTitle)
                    .replace("/", "")
                    .replace("\\", "")
                    .replace(":", "")
                    .replace("*", "")
                    .replace("?", "")
                    .replace("\"", "")
                    .replace("<", "")
                    .replace(">", "")
                    .replace("|", "");

            if (cleanTitle.isBlank()) {
                cleanTitle = "unnamed";
            }

            Path parentDir = oldPath.getParent();
            String newFilename = cleanTitle + extension;
            Path newPath = parentDir.resolve(newFilename);

            int count = 1;
            while (Files.exists(newPath)) {
                newFilename = cleanTitle + "_" + count + extension;
                newPath = parentDir.resolve(newFilename);
                count++;
            }

            System.gc();

            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);

            Path relativePath = this.uploadDir.relativize(newPath);
            return relativePath.toString().replace("\\", "/");
        } catch (Exception e) {
            return oldRelativePath;
        }
    }

    public String moveFile(String oldRelativePath, String newFolderName) {
        try {
            Path oldPath = this.uploadDir.resolve(oldRelativePath).normalize();
            if (!oldPath.startsWith(this.uploadDir) || !Files.exists(oldPath)) {
                return oldRelativePath;
            }

            Path targetDir = this.uploadDir;
            if (newFolderName != null && !newFolderName.isBlank()) {
                String cleanFolderName = StringUtils.cleanPath(newFolderName).replace("/", "").replace("\\", "");
                targetDir = this.uploadDir.resolve(cleanFolderName);
            }

            Files.createDirectories(targetDir);

            String filename = oldPath.getFileName().toString();
            String extension = "";
            String baseName = filename;
            int dotIndex = filename.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = filename.substring(dotIndex);
                baseName = filename.substring(0, dotIndex);
            }

            String newFilename = filename;
            Path newPath = targetDir.resolve(newFilename);

            int count = 1;
            while (Files.exists(newPath)) {
                newFilename = baseName + "_" + count + extension;
                newPath = targetDir.resolve(newFilename);
                count++;
            }

            System.gc();

            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);

            Path relativePath = this.uploadDir.relativize(newPath);
            return relativePath.toString().replace("\\", "/");
        } catch (Exception e) {
            return oldRelativePath;
        }
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
