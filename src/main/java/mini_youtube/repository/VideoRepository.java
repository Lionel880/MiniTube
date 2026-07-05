package mini_youtube.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mini_youtube.entity.User;
import mini_youtube.entity.Video;

public interface VideoRepository extends JpaRepository<Video, Long> {

    Page<Video> findByUploaderUsername(String username, Pageable pageable);

    java.util.List<Video> findByUploader(User uploader);

    @Query("""
            select v from Video v
            where v.uploader.username = :username
              and (lower(v.title) like lower(concat('%', :keyword, '%'))
                   or lower(v.description) like lower(concat('%', :keyword, '%')))
            """)
    Page<Video> searchByUploader(
            @Param("username") String username,
            @Param("keyword") String keyword,
            Pageable pageable);
}
