package mini_youtube.dto.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "電子信箱不能為空")
    @Email(message = "電子信箱格式不正確")
    private String email;

    private String oldPassword;
    private String newPassword;
}
