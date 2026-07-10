package mini_youtube.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mini_youtube.entity.Folder;
import mini_youtube.entity.User;
import mini_youtube.entity.Video;

public interface VideoRepository extends JpaRepository<Video, Long> {

    Page<Video> findByUploaderUsername(String username, Pageable pageable);

    Page<Video> findByUploaderUsernameAndFolderId(String username, Long folderId, Pageable pageable);

    Page<Video> findByUploaderUsernameAndFolderIsNull(String username, Pageable pageable);

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

    @Query("""
            select v from Video v
            where v.uploader.username = :username
              and v.folder.id = :folderId
              and (lower(v.title) like lower(concat('%', :keyword, '%'))
                   or lower(v.description) like lower(concat('%', :keyword, '%')))
            """)
    Page<Video> searchByUploaderAndFolder(
            @Param("username") String username,
            @Param("keyword") String keyword,
            @Param("folderId") Long folderId,
            Pageable pageable);

    @Query("""
            select v from Video v
            where v.uploader.username = :username
              and v.folder is null
              and (lower(v.title) like lower(concat('%', :keyword, '%'))
                   or lower(v.description) like lower(concat('%', :keyword, '%')))
            """)
    Page<Video> searchByUploaderAndFolderIsNull(
            @Param("username") String username,
            @Param("keyword") String keyword,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Video v set v.folder = null where v.folder.id = :folderId")
    void detachFolder(@Param("folderId") Long folderId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Video v set v.folder = :folder where v.id in :ids and v.uploader = :uploader")
    void batchMoveToFolder(@Param("ids") java.util.List<Long> ids, @Param("folder") Folder folder, @Param("uploader") User uploader);
}
