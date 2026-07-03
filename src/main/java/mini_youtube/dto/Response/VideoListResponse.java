package mini_youtube.dto.Response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VideoListResponse {

    private List<VideoSummaryResponse> videos;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
}
