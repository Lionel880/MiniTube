package mini_youtube;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import mini_youtube.dto.Request.LoginRequest;
import mini_youtube.dto.Request.RegisterRequest;
import mini_youtube.dto.Response.LoginResponse;
import mini_youtube.dto.Response.RegisterResponse;
import mini_youtube.service.UserService;

@SpringBootTest
public class LoginApiTest {

    @Autowired
    private UserService userService;

    @Test
    public void testRegisterAndLogin() {
        String randomUsername = "user_" + UUID.randomUUID().toString().substring(0, 8);
        String password = "password123";
        String email = randomUsername + "@example.com";

        // 1. Test Register
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(randomUsername);
        registerRequest.setPassword(password);
        registerRequest.setEmail(email);

        RegisterResponse registerResponse = userService.register(registerRequest);
        assertNotNull(registerResponse.getId());
        assertEquals(randomUsername, registerResponse.getUsername());
        assertEquals(email, registerResponse.getEmail());

        // 2. Test Login Success
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(randomUsername);
        loginRequest.setPassword(password);

        LoginResponse loginResponse = userService.login(loginRequest);
        assertNotNull(loginResponse);
        assertNotNull(loginResponse.getToken());
        assertTrue(loginResponse.getToken().startsWith("eyJ"));

        // 3. Test Login Failure - Wrong Password
        LoginRequest wrongPasswordRequest = new LoginRequest();
        wrongPasswordRequest.setUsername(randomUsername);
        wrongPasswordRequest.setPassword("wrong_password");

        assertThrows(RuntimeException.class, () -> {
            userService.login(wrongPasswordRequest);
        });

        // 4. Test Login Failure - Non-existent User
        LoginRequest nonExistentUserRequest = new LoginRequest();
        nonExistentUserRequest.setUsername("non_existent_user_" + UUID.randomUUID());
        nonExistentUserRequest.setPassword(password);

        assertThrows(RuntimeException.class, () -> {
            userService.login(nonExistentUserRequest);
        });
    }

    @Test
    public void testLoginLockout() {
        String username = "lockout_user_" + UUID.randomUUID().toString().substring(0, 8);
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword("wrong_password");

        // 登入失敗 5 次
        for (int i = 0; i < 5; i++) {
            assertThrows(RuntimeException.class, () -> {
                userService.login(request);
            });
        }

        // 第 6 次登入應被直接攔截並拋出 "帳號已被暫時鎖定" 異常
        Exception exception = assertThrows(RuntimeException.class, () -> {
            userService.login(request);
        });

        assertTrue(exception.getMessage().contains("帳號已被暫時鎖定"), 
                   "應拋出帳號鎖定異常，實際訊息: " + exception.getMessage());
    }
}
