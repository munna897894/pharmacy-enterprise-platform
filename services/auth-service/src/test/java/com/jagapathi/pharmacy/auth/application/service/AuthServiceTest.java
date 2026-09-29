package com.jagapathi.pharmacy.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import com.jagapathi.pharmacy.auth.api.dto.LoginRequest;
import com.jagapathi.pharmacy.auth.api.dto.RegisterRequest;
import com.jagapathi.pharmacy.auth.api.dto.TokenResponse;
import com.jagapathi.pharmacy.auth.api.dto.UserResponse;
import com.jagapathi.pharmacy.auth.application.exception.InvalidCredentialsException;
import com.jagapathi.pharmacy.auth.application.exception.InvalidTokenException;
import com.jagapathi.pharmacy.auth.application.exception.UserAlreadyExistsException;
import com.jagapathi.pharmacy.auth.domain.RefreshToken;
import com.jagapathi.pharmacy.auth.domain.Role;
import com.jagapathi.pharmacy.auth.domain.User;
import com.jagapathi.pharmacy.auth.infrastructure.RefreshTokenRepository;
import com.jagapathi.pharmacy.auth.infrastructure.UserRepository;
import com.jagapathi.pharmacy.auth.infrastructure.jwt.JwtProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProvider jwtProvider;

    private AuthService authService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtProvider);
    }

    @Test
    void testRegisterUserSuccess() {
        RegisterRequest request = new RegisterRequest(
                "john@example.com",
                "john_doe",
                "Password123!",
                "John",
                "Doe",
                Set.of(Role.CUSTOMER)
        );

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        UserResponse response = authService.registerUser(request);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.username()).isEqualTo("john_doe");
        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.lastName()).isEqualTo("Doe");
        assertThat(response.roles()).containsExactly(Role.CUSTOMER);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void testRegisterUserEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "john@example.com",
                "john_doe",
                "Password123!",
                "John",
                "Doe",
                Set.of(Role.CUSTOMER)
        );

        User existingUser = new User(UUID.randomUUID(), "existing_user", "john@example.com", "hash", "Jane", "Doe", Set.of(Role.CUSTOMER));
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> authService.registerUser(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("Email already exists");
    }

    @Test
    void testRegisterUserUsernameAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "john@example.com",
                "john_doe",
                "Password123!",
                "John",
                "Doe",
                Set.of(Role.CUSTOMER)
        );

        User existingUser = new User(UUID.randomUUID(), "john_doe", "existing@example.com", "hash", "Jane", "Doe", Set.of(Role.CUSTOMER));
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> authService.registerUser(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("Username already exists");
    }

    @Test
    void testRegisterUserInvalidPassword() {
        RegisterRequest request = new RegisterRequest(
                "john@example.com",
                "john_doe",
                "weakpassword",
                "John",
                "Doe",
                Set.of(Role.CUSTOMER)
        );

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAuthenticateUserSuccess() {
        UUID userId = UUID.randomUUID();
        String password = "Password123!";
        String hashedPassword = passwordEncoder.encode(password);

        User user = new User(userId, "john_doe", "john@example.com", hashedPassword, "John", "Doe", Set.of(Role.CUSTOMER));

        LoginRequest request = new LoginRequest("john_doe", password);

        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(jwtProvider.generateAccessToken(userId, "john_doe", Set.of(Role.CUSTOMER))).thenReturn("access_token");
        when(jwtProvider.generateRefreshToken(userId)).thenReturn("refresh_token");
        when(jwtProvider.getAccessTokenExpirySeconds()).thenReturn(900L);
        when(jwtProvider.getRefreshTokenExpirySeconds()).thenReturn(604800L);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = authService.authenticateUser(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access_token");
        assertThat(response.refreshToken()).isEqualTo("refresh_token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);
        assertThat(response.roles()).containsExactly(Role.CUSTOMER);
    }

    @Test
    void testAuthenticateUserInvalidPassword() {
        UUID userId = UUID.randomUUID();
        String password = "Password123!";
        String hashedPassword = passwordEncoder.encode(password);

        User user = new User(userId, "john_doe", "john@example.com", hashedPassword, "John", "Doe", Set.of(Role.CUSTOMER));

        LoginRequest request = new LoginRequest("john_doe", "WrongPassword123!");

        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.authenticateUser(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void testAuthenticateUserNotFound() {
        LoginRequest request = new LoginRequest("nonexistent_user", "Password123!");

        when(userRepository.findByUsername("nonexistent_user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticateUser(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void testAuthenticateInactiveUser() {
        UUID userId = UUID.randomUUID();
        String password = "Password123!";
        String hashedPassword = passwordEncoder.encode(password);

        User user = new User(userId, "john_doe", "john@example.com", hashedPassword, "John", "Doe", Set.of(Role.CUSTOMER));
        user.setActive(false);

        LoginRequest request = new LoginRequest("john_doe", password);

        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.authenticateUser(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("User account is not active");
    }

    @Test
    void testRefreshAccessTokenRevokesOldRefreshToken() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "john_doe", "john@example.com", "hash", "John", "Doe", Set.of(Role.CUSTOMER));
        String oldRefreshToken = "old-refresh-token";

        Claims claims = Jwts.claims().subject(userId.toString()).build();
        RefreshToken storedToken = new RefreshToken(UUID.randomUUID(), userId, hashOf(oldRefreshToken), java.time.Instant.now().plusSeconds(3600));

        when(jwtProvider.validateToken(oldRefreshToken)).thenReturn(claims);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHash(hashOf(oldRefreshToken))).thenReturn(Optional.of(storedToken));
        when(jwtProvider.generateAccessToken(userId, "john_doe", Set.of(Role.CUSTOMER))).thenReturn("new_access_token");
        when(jwtProvider.generateRefreshToken(userId)).thenReturn("new_refresh_token");
        when(jwtProvider.getAccessTokenExpirySeconds()).thenReturn(900L);
        when(jwtProvider.getRefreshTokenExpirySeconds()).thenReturn(604800L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = authService.refreshAccessToken(oldRefreshToken);

        assertThat(response.accessToken()).isEqualTo("new_access_token");
        assertThat(response.refreshToken()).isEqualTo("new_refresh_token");
        assertThat(storedToken.getRevokedAt()).isNotNull();
        assertThat(storedToken.isValid()).isFalse();
    }

    @Test
    void testRefreshAccessTokenRejectsAlreadyRevokedToken() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "john_doe", "john@example.com", "hash", "John", "Doe", Set.of(Role.CUSTOMER));
        String refreshToken = "reused-refresh-token";

        Claims claims = Jwts.claims().subject(userId.toString()).build();
        RefreshToken storedToken = new RefreshToken(UUID.randomUUID(), userId, hashOf(refreshToken), java.time.Instant.now().plusSeconds(3600));
        storedToken.revoke();

        when(jwtProvider.validateToken(refreshToken)).thenReturn(claims);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHash(hashOf(refreshToken))).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refreshAccessToken(refreshToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void testLogoutRevokesRefreshToken() {
        UUID userId = UUID.randomUUID();
        String refreshToken = "some-refresh-token";
        RefreshToken storedToken = new RefreshToken(UUID.randomUUID(), userId, hashOf(refreshToken), java.time.Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(hashOf(refreshToken))).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.logout(refreshToken);

        assertThat(storedToken.getRevokedAt()).isNotNull();
        assertThat(storedToken.isValid()).isFalse();
    }

    @Test
    void testLogoutTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(hashOf("unknown-token"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout("unknown-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    private String hashOf(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
