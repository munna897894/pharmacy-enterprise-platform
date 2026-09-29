package com.jagapathi.pharmacy.auth.api.dto;

import java.util.Set;
import java.util.UUID;

import com.jagapathi.pharmacy.auth.domain.Role;

public record UserResponse(
        UUID id,
        String email,
        String username,
        String firstName,
        String lastName,
        Set<Role> roles,
        boolean isActive) {
}
