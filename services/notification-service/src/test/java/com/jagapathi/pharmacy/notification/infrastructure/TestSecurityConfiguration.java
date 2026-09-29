package com.jagapathi.pharmacy.notification.infrastructure;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;

@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("test")
public class TestSecurityConfiguration {

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> {
            try {
                return new org.springframework.security.oauth2.jwt.Jwt(
                    token,
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    java.util.Map.of("alg", "RS256"),
                    java.util.Map.of(
                        "sub", "550e8400-e29b-41d4-a716-446655440000",
                        "roles", java.util.List.of("CUSTOMER")
                    )
                );
            } catch (Exception e) {
                throw new org.springframework.security.oauth2.jwt.JwtException("Invalid JWT", e);
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        
        return http.build();
    }
}

