package mini_youtube.dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateVideoRequest {

    @NotBlank(message = "影片標題不能為空")
    @Size(max = 200, message = "影片標題不能超過 200 個字")
    private String title;

    @Size(max = 2000, message = "影片描述不能超過 2000 個字")
    private String description;
}
