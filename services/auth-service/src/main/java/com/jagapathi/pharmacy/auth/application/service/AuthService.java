package com.jagapathi.pharmacy.auth.application.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jagapathi.pharmacy.auth.api.dto.LoginRequest;
import com.jagapathi.pharmacy.auth.api.dto.RegisterRequest;
import com.jagapathi.pharmacy.auth.api.dto.TokenResponse;
import com.jagapathi.pharmacy.auth.api.dto.UserResponse;
import com.jagapathi.pharmacy.auth.application.exception.InvalidCredentialsException;
import com.jagapathi.pharmacy.auth.application.exception.InvalidTokenException;
import com.jagapathi.pharmacy.auth.application.exception.UserAlreadyExistsException;
import com.jagapathi.pharmacy.auth.application.exception.UserNotFoundException;
import com.jagapathi.pharmacy.auth.application.exception.AuthException;
import com.jagapathi.pharmacy.auth.domain.RefreshToken;
import com.jagapathi.pharmacy.auth.domain.Role;
import com.jagapathi.pharmacy.auth.domain.User;
import com.jagapathi.pharmacy.auth.infrastructure.RefreshTokenRepository;
import com.jagapathi.pharmacy.auth.infrastructure.UserRepository;
import com.jagapathi.pharmacy.auth.infrastructure.jwt.JwtProvider;
import com.jagapathi.pharmacy.auth.infrastructure.util.PasswordValidator;

import io.jsonwebtoken.Claims;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public UserResponse registerUser(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            logger.debug("auth.registration.completed outcome=rejected reason=duplicate_email");
            throw new UserAlreadyExistsException("Email already exists");
        }

        if (userRepository.findByUsername(request.username()).isPresent()) {
            logger.debug("auth.registration.completed outcome=rejected reason=duplicate_username");
            throw new UserAlreadyExistsException("Username already exists");
        }

        if (!PasswordValidator.isValid(request.password())) {
            throw new IllegalArgumentException(PasswordValidator.getValidationMessage());
        }

        UUID userId = UUID.randomUUID();
        String hashedPassword = passwordEncoder.encode(request.password());

        User user = new User(
                userId,
                request.username(),
                request.email(),
                hashedPassword,
                request.firstName(),
                request.lastName(),
                request.roles()
        );

        User savedUser = userRepository.save(user);
        logger.info("auth.registration.completed userId={} outcome=created", savedUser.getId());

        return toUserResponse(savedUser);
    }

    @Transactional
    public TokenResponse authenticateUser(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    logger.debug("auth.login.completed outcome=rejected reason=invalid_credentials");
                    return new InvalidCredentialsException("Invalid username or password");
                });

        if (!user.isActive()) {
            logger.debug("auth.login.completed outcome=rejected reason=inactive");
            throw new InvalidCredentialsException("User account is not active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            logger.debug("auth.login.completed outcome=rejected reason=invalid_credentials");
            throw new InvalidCredentialsException("Invalid username or password");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRoles());
        String refreshToken = generateAndStoreRefreshToken(user.getId());

        logger.info("auth.login.completed userId={} outcome=authenticated", user.getId());

        return new TokenResponse(
                accessToken,
                "Bearer",
                jwtProvider.getAccessTokenExpirySeconds(),
                refreshToken,
                user.getRoles()
        );
    }

    @Transactional
    public TokenResponse refreshAccessToken(String refreshToken) {
        logger.debug("Refreshing access token");

        try {
            Claims claims = jwtProvider.validateToken(refreshToken);
            UUID userId = UUID.fromString(claims.getSubject());

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));

            if (!user.isActive()) {
                throw new InvalidTokenException("User account is not active");
            }

            String tokenHash = hashToken(refreshToken);
            RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                    .orElseThrow(() -> new InvalidTokenException("Refresh token not found or revoked"));

            if (!storedToken.isValid()) {
                throw new InvalidTokenException("Refresh token is expired or revoked");
            }

            storedToken.revoke();
            refreshTokenRepository.save(storedToken);

            String newAccessToken = jwtProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRoles());
            String newRefreshToken = generateAndStoreRefreshToken(user.getId());

            logger.info("Access token refreshed successfully for user: {}", userId);

            return new TokenResponse(
                    newAccessToken,
                    "Bearer",
                    jwtProvider.getAccessTokenExpirySeconds(),
                    newRefreshToken,
                    user.getRoles()
            );
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            logger.debug("auth.token.refresh outcome=rejected");
            throw new InvalidTokenException("Invalid or expired refresh token", e);
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return toUserResponse(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        logger.debug("Logout request received");

        String tokenHash = hashToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found or revoked"));

        storedToken.revoke();
        refreshTokenRepository.save(storedToken);

        logger.info("User logged out successfully: {}", storedToken.getUserId());
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsersByRole(Role role, Pageable pageable) {
        return userRepository.findByRole(role, pageable)
                .map(this::toUserResponse);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listActiveUsers(Pageable pageable) {
        return userRepository.findActiveUsers(pageable)
                .map(this::toUserResponse);
    }

    private String generateAndStoreRefreshToken(UUID userId) {
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        String tokenHash = hashToken(refreshToken);

        Instant expiresAt = Instant.now().plusSeconds(jwtProvider.getRefreshTokenExpirySeconds());

        RefreshToken token = new RefreshToken(UUID.randomUUID(), userId, tokenHash, expiresAt);
        refreshTokenRepository.save(token);

        return refreshToken;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AuthException("Failed to hash token", e);
        }
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getRoles(),
                user.isActive()
        );
    }
}
