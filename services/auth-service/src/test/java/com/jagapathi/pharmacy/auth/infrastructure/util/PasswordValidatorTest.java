package com.jagapathi.pharmacy.auth.infrastructure.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordValidatorTest {

    @Test
    void testValidPassword() {
        assertThat(PasswordValidator.isValid("MyPassword123!")).isTrue();
        assertThat(PasswordValidator.isValid("SecurePass@2024")).isTrue();
        assertThat(PasswordValidator.isValid("Test1234!")).isTrue();
    }

    @Test
    void testPasswordTooShort() {
        assertThat(PasswordValidator.isValid("Pass1!")).isFalse();
    }

    @Test
    void testPasswordMissingUppercase() {
        assertThat(PasswordValidator.isValid("password123!")).isFalse();
    }

    @Test
    void testPasswordMissingLowercase() {
        assertThat(PasswordValidator.isValid("PASSWORD123!")).isFalse();
    }

    @Test
    void testPasswordMissingDigit() {
        assertThat(PasswordValidator.isValid("MyPassword!")).isFalse();
    }

    @Test
    void testPasswordMissingSpecialChar() {
        assertThat(PasswordValidator.isValid("MyPassword123")).isFalse();
    }

    @Test
    void testNullPassword() {
        assertThat(PasswordValidator.isValid(null)).isFalse();
    }
}
