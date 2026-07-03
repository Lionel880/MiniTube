package mini_youtube.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mini_youtube.entity.User;

public interface UserRepository 
    extends JpaRepository<User, Long>{
        Optional<User> findByUsername(String username);
    }
