package mini_youtube.dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateFolderRequest {

    @NotBlank(message = "資料夾名稱不能為空")
    @Size(max = 100, message = "資料夾名稱不能超過 100 個字")
    private String name;
}
