package com.jagapathi.pharmacy.auth.infrastructure.util;

import java.util.regex.Pattern;

public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SPECIAL_CHAR = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    public static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        return UPPERCASE.matcher(password).find()
                && LOWERCASE.matcher(password).find()
                && DIGIT.matcher(password).find()
                && SPECIAL_CHAR.matcher(password).find();
    }

    public static String getValidationMessage() {
        return "Password must be at least 8 characters and contain at least one uppercase letter, one lowercase letter, one digit, and one special character";
    }
}
