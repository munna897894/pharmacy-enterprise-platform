package com.jagapathi.pharmacy.auth.api.dto;

import java.util.Set;

import com.jagapathi.pharmacy.auth.domain.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "Username must be 3-20 characters, alphanumeric and underscore only")
        String username,

        @NotBlank(message = "Password is required")
        String password,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotEmpty(message = "At least one role is required")
        Set<Role> roles) {
}
