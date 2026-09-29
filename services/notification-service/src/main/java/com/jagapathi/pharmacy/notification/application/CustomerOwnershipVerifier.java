package com.jagapathi.pharmacy.notification.application;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface CustomerOwnershipVerifier {

    boolean isOwner(UUID customerId, Jwt callerJwt);
}
