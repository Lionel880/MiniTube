package mini_youtube.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mini_youtube.dto.Request.UpdateProfileRequest;
import mini_youtube.dto.Response.UserProfileResponse;
import mini_youtube.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        UserProfileResponse response = userService.getProfile(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserProfileResponse response = userService.updateProfile(authentication.getName(), request);
        return ResponseEntity.ok(response);
    }
}
