package com.jagapathi.pharmacy.auth.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jagapathi.pharmacy.auth.domain.Role;
import com.jagapathi.pharmacy.auth.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    @Query(value = """
            SELECT * FROM app_user u
            WHERE CAST(u.roles AS CHAR(255)) LIKE CONCAT('%', CHAR(34), :roleName, CHAR(34), '%')
            """, nativeQuery = true)
    Page<User> findByRoleName(@Param("roleName") String roleName, Pageable pageable);

    default Page<User> findByRole(Role role, Pageable pageable) {
        return findByRoleName(role.name(), pageable);
    }

    @Query("SELECT u FROM User u WHERE u.isActive = true")
    Page<User> findActiveUsers(Pageable pageable);
}
