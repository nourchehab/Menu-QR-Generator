package com.restaurant.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.restaurant.admin.model.SimpleUser;

public interface SimpleUserRepository extends JpaRepository<SimpleUser, Long> {
    boolean existsByEmail(String email);
    Optional<SimpleUser> findByEmail(String email);
}
