package com.jagapathi.pharmacy.order.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.KeyGenerator;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public UserDetailsService userDetailsService() {
        UserDetails customer = User.builder()
            .username("customer@test.com")
            .password("password")
            .authorities("ROLE_CUSTOMER")
            .build();

        UserDetails admin = User.builder()
            .username("admin@test.com")
            .password("password")
            .authorities("ROLE_ADMIN", "ROLE_STORE_MANAGER")
            .build();

        return new InMemoryUserDetailsManager(customer, admin);
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}

