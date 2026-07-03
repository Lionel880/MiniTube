package mini_youtube.dto.Response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VideoSummaryResponse {

    private Long id;
    private String title;
    private String coverUrl;
    private String uploaderUsername;
    private long viewCount;
    private Long fileSize;
    private LocalDateTime createdAt;
}
