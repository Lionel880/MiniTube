package mini_youtube.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {

    private String token;
    private String username;

    /** 只帶 token 的便利建構子，username 之後由 Controller 設定 */
    public LoginResponse(String token) {
        this.token = token;
    }
}
