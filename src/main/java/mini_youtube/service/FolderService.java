package mini_youtube.service;

import lombok.RequiredArgsConstructor;
import mini_youtube.dto.Request.CreateFolderRequest;
import mini_youtube.dto.Response.FolderResponse;
import mini_youtube.entity.Folder;
import mini_youtube.entity.User;
import mini_youtube.repository.FolderRepository;
import mini_youtube.repository.UserRepository;
import mini_youtube.repository.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;

    @Transactional(readOnly = true)
    public List<FolderResponse> getFoldersByOwner(String username) {
        return folderRepository.findByOwnerUsername(username).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public FolderResponse createFolder(String username, CreateFolderRequest request) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("使用者不存在"));

        if (folderRepository.existsByNameAndOwner(request.getName(), owner)) {
            throw new IllegalArgumentException("同名的資料夾已存在");
        }

        Folder folder = Folder.builder()
                .name(request.getName())
                .owner(owner)
                .build();

        Folder saved = folderRepository.save(folder);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteFolder(String username, Long folderId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("資料夾不存在"));

        if (!folder.getOwner().getUsername().equals(username)) {
            throw new IllegalArgumentException("您無權限刪除此資料夾");
        }

        // 刪除前先將該資料夾底下的所有影片移出至根目錄
        videoRepository.detachFolder(folderId);

        folderRepository.delete(folder);
    }

    private FolderResponse mapToResponse(Folder folder) {
        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .createdAt(folder.getCreatedAt())
                .build();
    }
}
