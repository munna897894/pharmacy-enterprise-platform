package com.jagapathi.pharmacy.auth.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.jagapathi.pharmacy.auth.domain.Role;
import com.jagapathi.pharmacy.auth.domain.User;
import com.jagapathi.pharmacy.auth.infrastructure.UserRepository;

@DataJpaTest
@TestPropertySource(locations = "classpath:application.yml")
class UserRepositoryIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User(
                UUID.randomUUID(),
                "testuser",
                "test@example.com",
                "hashed_password",
                "Test",
                "User",
                Set.of(Role.CUSTOMER)
        );
    }

    @Test
    void testFindByEmail() {
        userRepository.save(testUser);

        Optional<User> found = userRepository.findByEmail("test@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void testFindByEmailNotFound() {
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void testFindByUsername() {
        userRepository.save(testUser);

        Optional<User> found = userRepository.findByUsername("testuser");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void testFindByUsernameNotFound() {
        Optional<User> found = userRepository.findByUsername("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void testFindByRole() {
        User pharmacist = new User(
                UUID.randomUUID(),
                "pharmacist_user",
                "pharmacist@example.com",
                "hashed_password",
                "Pharmacist",
                "User",
                Set.of(Role.PHARMACIST)
        );

        userRepository.save(testUser);
        userRepository.save(pharmacist);
        userRepository.flush();
        String storedRoles = jdbcTemplate.queryForObject(
                "SELECT CAST(roles AS VARCHAR) FROM app_user WHERE username = ?",
                String.class,
                "testuser");

        Pageable pageable = PageRequest.of(0, 10);
        Page<User> customers = userRepository.findByRole(Role.CUSTOMER, pageable);

        assertThat(storedRoles).isEqualTo("[\"CUSTOMER\"]");
        assertThat(customers.getContent()).hasSize(1);
        assertThat(customers.getContent().get(0).getUsername()).isEqualTo("testuser");
    }

    @Test
    void testFindActiveUsers() {
        User inactiveUser = new User(
                UUID.randomUUID(),
                "inactive_user",
                "inactive@example.com",
                "hashed_password",
                "Inactive",
                "User",
                Set.of(Role.CUSTOMER)
        );
        inactiveUser.setActive(false);

        userRepository.save(testUser);
        userRepository.save(inactiveUser);

        Pageable pageable = PageRequest.of(0, 10);
        Page<User> activeUsers = userRepository.findActiveUsers(pageable);

        assertThat(activeUsers.getContent()).hasSize(1);
        assertThat(activeUsers.getContent().get(0).getUsername()).isEqualTo("testuser");
        assertThat(activeUsers.getContent().get(0).isActive()).isTrue();
    }

    @Test
    void testUserEmailUniqueness() {
        userRepository.save(testUser);

        User duplicateEmail = new User(
                UUID.randomUUID(),
                "different_user",
                "test@example.com",
                "hashed_password",
                "Different",
                "User",
                Set.of(Role.CUSTOMER)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userRepository.saveAndFlush(duplicateEmail))
                .isNotNull();
    }

    @Test
    void testUserUsernameUniqueness() {
        userRepository.save(testUser);

        User duplicateUsername = new User(
                UUID.randomUUID(),
                "testuser",
                "different@example.com",
                "hashed_password",
                "Different",
                "User",
                Set.of(Role.CUSTOMER)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userRepository.saveAndFlush(duplicateUsername))
                .isNotNull();
    }

    private static <T extends Exception> void assertThatThrownBy(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected exception to be thrown");
        } catch (Exception e) {
            if (e.getMessage().contains("Duplicate entry")) {
                // Expected
            } else {
                throw e;
            }
        }
    }
}
