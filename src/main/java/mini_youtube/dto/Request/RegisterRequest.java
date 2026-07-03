package mini_youtube.dto.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "使用者名稱不能為空")
    @Size(min = 3, max = 50, message = "使用者名稱長度需介於 3 到 50 個字元")
    private String username;

    @NotBlank(message = "Email 不能為空")
    @Email(message = "Email 格式不正確")
    @Size(max = 100, message = "Email 長度不能超過 100 個字元")
    private String email;

    @NotBlank(message = "密碼不能為空")
    @Size(min = 6, max = 100, message = "密碼長度需介於 6 到 100 個字元")
    private String password;
}
