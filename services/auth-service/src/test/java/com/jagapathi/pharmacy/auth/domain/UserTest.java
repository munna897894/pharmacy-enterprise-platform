package com.jagapathi.pharmacy.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class UserTest {

    @Test
    void testUserCreation() {
        UUID userId = UUID.randomUUID();
        Set<Role> roles = Set.of(Role.CUSTOMER);

        User user = new User(userId, "john_doe", "john@example.com", "hashed_password",
                "John", "Doe", roles);

        assertThat(user.getId()).isEqualTo(userId);
        assertThat(user.getUsername()).isEqualTo("john_doe");
        assertThat(user.getEmail()).isEqualTo("john@example.com");
        assertThat(user.getPassword()).isEqualTo("hashed_password");
        assertThat(user.getFirstName()).isEqualTo("John");
        assertThat(user.getLastName()).isEqualTo("Doe");
        assertThat(user.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void testUserRoleAssignment() {
        UUID userId = UUID.randomUUID();
        Set<Role> roles = Set.of(Role.PHARMACIST, Role.ADMIN);

        User user = new User(userId, "admin_user", "admin@example.com", "hashed_password",
                "Admin", "User", roles);

        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.PHARMACIST, Role.ADMIN);
    }

    @Test
    void testUserAuthorities() {
        UUID userId = UUID.randomUUID();
        Set<Role> roles = Set.of(Role.CUSTOMER, Role.PHARMACIST);

        User user = new User(userId, "test_user", "test@example.com", "hashed_password",
                "Test", "User", roles);

        Set<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(authorities).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_PHARMACIST");
    }

    @Test
    void testUserIsEnabled() {
        UUID userId = UUID.randomUUID();
        Set<Role> roles = Set.of(Role.CUSTOMER);

        User user = new User(userId, "john_doe", "john@example.com", "hashed_password",
                "John", "Doe", roles);

        assertThat(user.isEnabled()).isTrue();

        user.setActive(false);
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void testUserInactiveStatus() {
        UUID userId = UUID.randomUUID();
        Set<Role> roles = Set.of(Role.CUSTOMER);

        User user = new User(userId, "john_doe", "john@example.com", "hashed_password",
                "John", "Doe", roles);

        user.setActive(false);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void testUserAccountProperties() {
        UUID userId = UUID.randomUUID();
        Set<Role> roles = Set.of(Role.CUSTOMER);

        User user = new User(userId, "john_doe", "john@example.com", "hashed_password",
                "John", "Doe", roles);

        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
    }
}
