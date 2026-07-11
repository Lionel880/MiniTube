package mini_youtube.repository;

import mini_youtube.entity.Folder;
import mini_youtube.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByOwner(User owner);
    List<Folder> findByOwnerUsername(String username);
    List<Folder> findByOwnerUsernameAndParentIsNull(String username);
    List<Folder> findByOwnerUsernameAndParentId(String username, Long parentId);
    List<Folder> findByParentId(Long parentId);
    boolean existsByNameAndOwner(String name, User owner);
    boolean existsByNameAndOwnerAndParent(String name, User owner, Folder parent);
    boolean existsByNameAndOwnerAndParentIsNull(String name, User owner);
}
