package mini_youtube.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mini_youtube.exception.BusinessException;
import mini_youtube.dto.Request.LoginRequest;
import mini_youtube.dto.Request.RegisterRequest;
import mini_youtube.dto.Request.UpdateProfileRequest;
import mini_youtube.dto.Response.LoginResponse;
import mini_youtube.dto.Response.RegisterResponse;
import mini_youtube.dto.Response.UserProfileResponse;
import mini_youtube.entity.User;
import mini_youtube.repository.UserRepository;
import mini_youtube.security.JwtUtil;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    public RegisterResponse register(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        
        User savedUser = userRepository.save(user);

        return new RegisterResponse(
            savedUser.getId(),
            savedUser.getUsername(),
            savedUser.getEmail()
        );
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();
        if (loginAttemptService.isBlocked(username)) {
            throw new BusinessException("帳號已被暫時鎖定，請於 15 分鐘後再試");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    loginAttemptService.loginFailed(username);
                    return new BusinessException("帳號或密碼錯誤");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.loginFailed(username);
            throw new BusinessException("帳號或密碼錯誤");
        }

        loginAttemptService.loginSucceeded(username);
        String token = jwtUtil.generateToken(user.getUsername());
        return new LoginResponse(token);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("使用者不存在: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities("USER")
                .build();
    }

    public UserProfileResponse getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("找不到該使用者"));
        return new UserProfileResponse(user.getUsername(), user.getEmail());
    }

    @org.springframework.transaction.annotation.Transactional
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("找不到該使用者"));

        // 檢查 Email 是否已被其他人使用
        if (!user.getEmail().equalsIgnoreCase(request.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("該電子信箱已被其他帳號使用");
            }
            user.setEmail(request.getEmail());
        }

        // 修改密碼邏輯
        if (request.getNewPassword() != null && !request.getNewPassword().trim().isEmpty()) {
            if (request.getOldPassword() == null || request.getOldPassword().isEmpty()) {
                throw new BusinessException("修改密碼必須輸入舊密碼");
            }
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new BusinessException("舊密碼不正確");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword().trim()));
        }

        User saved = userRepository.save(user);
        return new UserProfileResponse(saved.getUsername(), saved.getEmail());
    }
}
