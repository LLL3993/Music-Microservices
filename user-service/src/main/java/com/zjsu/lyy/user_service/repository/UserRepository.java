package com.zjsu.lyy.user_service.repository;

import com.zjsu.lyy.user_service.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByIdAndDeletedFalse(Long id);

	Optional<User> findByUsernameAndDeletedFalse(String username);

	boolean existsByUsernameAndDeletedFalse(String username);

	boolean existsByEmailAndDeletedFalse(String email);

	boolean existsByUsernameAndDeletedFalseAndIdNot(String username, Long id);

	boolean existsByEmailAndDeletedFalseAndIdNot(String email, Long id);
}
