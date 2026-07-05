package mini_youtube.dto.Response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mini_youtube.entity.VideoStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VideoDetailResponse {

    private Long id;
    private String title;
    private String description;
    private String videoUrl;
    private String coverUrl;
    private String uploaderUsername;
    private long viewCount;
    private Long fileSize;
    private VideoStatus status;
    private LocalDateTime createdAt;
}
