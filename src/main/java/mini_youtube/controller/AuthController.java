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
    public org.springframework.http.ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse loginResponse = userService.login(request);
        
        // 將 JWT Token 設定為 HttpOnly Cookie，阻止前端 JavaScript 讀取 (防範 XSS)
        org.springframework.http.ResponseCookie cookie = org.springframework.http.ResponseCookie.from("token", loginResponse.getToken())
                .httpOnly(true)
                .secure(true) // 在 HTTPS 與開發用 localhost 均為安全傳輸 (Chrome 支援 localhost HTTP 傳 Secure Cookie)
                .path("/")
                .sameSite("None") // 支援前後端跨域部署 (如 GitHub Pages 與 Render)
                .maxAge(7 * 24 * 60 * 60) // 7 天有效期限
                .build();

        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponse(null)); // 響應體清空 Token，避免暴露給前端 JS
    }

    @PostMapping("/logout")
    public org.springframework.http.ResponseEntity<Void> logout() {
        // 清除 Cookie
        org.springframework.http.ResponseCookie cookie = org.springframework.http.ResponseCookie.from("token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("None")
                .maxAge(0) // 立即過期
                .build();

        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    /** 讓前端可以用 Cookie 確認登入狀態、取得目前使用者名稱。 */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return new MeResponse(authentication.getName());
    }
}
