package com.jagapathi.pharmacy.auth.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.auth.api.dto.LoginRequest;
import com.jagapathi.pharmacy.auth.api.dto.RegisterRequest;
import com.jagapathi.pharmacy.auth.api.dto.RefreshTokenRequest;
import com.jagapathi.pharmacy.auth.domain.Role;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.yml")
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "newuser@example.com",
                "newuser123",
                "ValidPass123!",
                "New",
                "User",
                Set.of(Role.CUSTOMER)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.username").value("newuser123"))
                .andExpect(jsonPath("$.firstName").value("New"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void testRegisterInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "invalid-email",
                "newuser123",
                "ValidPass123!",
                "New",
                "User",
                Set.of(Role.CUSTOMER)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRegisterInvalidUsername() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "valid@example.com",
                "ab",
                "ValidPass123!",
                "New",
                "User",
                Set.of(Role.CUSTOMER)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLoginSuccess() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
                "testuser@example.com",
                "testuser_login",
                "ValidPass123!",
                "Test",
                "User",
                Set.of(Role.CUSTOMER)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("testuser_login", "ValidPass123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    }

    @Test
    void testLoginInvalidPassword() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
                "testuser2@example.com",
                "testuser_invalid",
                "ValidPass123!",
                "Test",
                "User",
                Set.of(Role.CUSTOMER)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("testuser_invalid", "WrongPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLoginUserNotFound() throws Exception {
        LoginRequest loginRequest = new LoginRequest("nonexistent_user", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRefreshTokenSuccess() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
                "testuser3@example.com",
                "testuser_refresh",
                "ValidPass123!",
                "Test",
                "User",
                Set.of(Role.CUSTOMER)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("testuser_refresh", "ValidPass123!");

        var loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = objectMapper.readTree(loginResponse.getResponse().getContentAsString()).get("refreshToken").asText();

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void testValidateEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/auth/validate")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
