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
    public List<FolderResponse> getFoldersByOwner(String username, Long parentId) {
        List<Folder> folders;
        if (parentId == null) {
            folders = folderRepository.findByOwnerUsernameAndParentIsNull(username);
        } else {
            folders = folderRepository.findByOwnerUsernameAndParentId(username, parentId);
        }
        return folders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public List<FolderResponse> getAllFoldersByOwner(String username) {
        return folderRepository.findByOwnerUsername(username).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public FolderResponse getFolderById(String username, Long id) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("資料夾不存在"));
        if (!folder.getOwner().getUsername().equals(username)) {
            throw new IllegalArgumentException("您無權限訪問此資料夾");
        }
        return mapToResponse(folder);
    }

    @Transactional
    public FolderResponse createFolder(String username, CreateFolderRequest request) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("使用者不存在"));

        Folder parent = null;
        if (request.getParentId() != null) {
            parent = folderRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("父資料夾不存在"));
            if (!parent.getOwner().getId().equals(owner.getId())) {
                throw new IllegalArgumentException("您無權限在此資料夾建立子資料夾");
            }
        }

        boolean exists = (parent == null)
                ? folderRepository.existsByNameAndOwnerAndParentIsNull(request.getName(), owner)
                : folderRepository.existsByNameAndOwnerAndParent(request.getName(), owner, parent);

        if (exists) {
            throw new IllegalArgumentException("同名的資料夾已存在");
        }

        Folder folder = Folder.builder()
                .name(request.getName())
                .owner(owner)
                .parent(parent)
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

        recursiveDeleteFolder(folder);
    }

    private void recursiveDeleteFolder(Folder folder) {
        List<Folder> subfolders = folderRepository.findByParentId(folder.getId());
        for (Folder sub : subfolders) {
            recursiveDeleteFolder(sub);
        }
        videoRepository.detachFolder(folder.getId());
        folderRepository.delete(folder);
    }

    @Transactional
    public FolderResponse updateFolder(String username, Long folderId, CreateFolderRequest request) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("資料夾不存在"));

        if (!folder.getOwner().getUsername().equals(username)) {
            throw new IllegalArgumentException("您無權編輯此資料夾");
        }

        User owner = folder.getOwner();
        Folder parent = folder.getParent();

        boolean exists = (parent == null)
                ? folderRepository.existsByNameAndOwnerAndParentIsNull(request.getName(), owner)
                : folderRepository.existsByNameAndOwnerAndParent(request.getName(), owner, parent);

        if (!folder.getName().equals(request.getName()) && exists) {
            throw new IllegalArgumentException("同名的資料夾已存在");
        }

        folder.setName(request.getName());
        Folder saved = folderRepository.save(folder);
        return mapToResponse(saved);
    }

    private FolderResponse mapToResponse(Folder folder) {
        List<FolderResponse> crumbs = new java.util.ArrayList<>();
        Folder current = folder;
        while (current != null) {
            crumbs.add(0, FolderResponse.builder()
                    .id(current.getId())
                    .name(current.getName())
                    .parentId(current.getParent() == null ? null : current.getParent().getId())
                    .build());
            current = current.getParent();
        }

        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .createdAt(folder.getCreatedAt())
                .parentId(folder.getParent() == null ? null : folder.getParent().getId())
                .breadcrumbs(crumbs)
                .build();
    }
}
