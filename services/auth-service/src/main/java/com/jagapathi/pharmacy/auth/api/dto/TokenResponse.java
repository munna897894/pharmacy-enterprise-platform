package com.jagapathi.pharmacy.auth.api.dto;

import java.util.Set;

import com.jagapathi.pharmacy.auth.domain.Role;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String refreshToken,
        Set<Role> roles) {
}
