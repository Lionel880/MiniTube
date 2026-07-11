package mini_youtube.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mini_youtube.dto.Request.CreateFolderRequest;
import mini_youtube.dto.Response.FolderResponse;
import mini_youtube.service.FolderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    @GetMapping
    public List<FolderResponse> getFolders(
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestParam(value = "parentId", required = false) Long parentId,
            @org.springframework.web.bind.annotation.RequestParam(value = "all", required = false, defaultValue = "false") boolean all) {
        if (all) {
            return folderService.getAllFoldersByOwner(authentication.getName());
        }
        return folderService.getFoldersByOwner(authentication.getName(), parentId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderResponse> getFolderById(
            Authentication authentication,
            @PathVariable("id") Long id) {
        FolderResponse response = folderService.getFolderById(authentication.getName(), id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
            Authentication authentication,
            @Valid @RequestBody CreateFolderRequest request) {
        FolderResponse response = folderService.createFolder(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FolderResponse> updateFolder(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody CreateFolderRequest request) {
        FolderResponse response = folderService.updateFolder(authentication.getName(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFolder(
            Authentication authentication,
            @PathVariable("id") Long id) {
        folderService.deleteFolder(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
