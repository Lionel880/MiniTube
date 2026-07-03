package mini_youtube.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mini_youtube.dto.Request.LoginRequest;
import mini_youtube.dto.Request.RegisterRequest;
import mini_youtube.dto.Response.LoginResponse;
import mini_youtube.dto.Response.MeResponse;
import mini_youtube.dto.Response.RegisterResponse;
import mini_youtube.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    /** 讓前端可以用現有 token 確認登入狀態、取得目前使用者名稱。 */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return new MeResponse(authentication.getName());
    }
}
