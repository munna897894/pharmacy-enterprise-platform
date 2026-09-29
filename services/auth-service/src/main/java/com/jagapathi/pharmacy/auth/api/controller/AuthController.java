package com.jagapathi.pharmacy.auth.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.jagapathi.pharmacy.auth.api.dto.LoginRequest;
import com.jagapathi.pharmacy.auth.api.dto.RefreshTokenRequest;
import com.jagapathi.pharmacy.auth.api.dto.RegisterRequest;
import com.jagapathi.pharmacy.auth.api.dto.TokenResponse;
import com.jagapathi.pharmacy.auth.api.dto.UserResponse;
import com.jagapathi.pharmacy.auth.application.service.AuthService;
import com.jagapathi.pharmacy.auth.infrastructure.jwt.JwtProvider;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtProvider jwtProvider;

    public AuthController(AuthService authService, JwtProvider jwtProvider) {
        this.authService = authService;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("Registration request received");
        UserResponse userResponse = authService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login request received");
        TokenResponse tokenResponse = authService.authenticateUser(request);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        logger.debug("Token refresh request");
        TokenResponse tokenResponse = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        logger.info("Logout request");
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/validate")
    public ResponseEntity<Void> validate() {
        logger.debug("Token validation request");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/.well-known/jwks.json")
    @ResponseBody
    public ResponseEntity<String> jwks() {
        return ResponseEntity.ok(jwtProvider.getJwksJson());
    }
}
