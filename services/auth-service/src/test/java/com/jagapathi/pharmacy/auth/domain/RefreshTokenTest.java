package com.jagapathi.pharmacy.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    @Test
    void testRefreshTokenCreation() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String tokenHash = "hashed_token";
        Instant expiresAt = Instant.now().plusSeconds(604800);

        RefreshToken token = new RefreshToken(tokenId, userId, tokenHash, expiresAt);

        assertThat(token.getId()).isEqualTo(tokenId);
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getTokenHash()).isEqualTo(tokenHash);
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    void testRefreshTokenIsValidWhenNotExpiredAndNotRevoked() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String tokenHash = "hashed_token";
        Instant expiresAt = Instant.now().plusSeconds(604800);

        RefreshToken token = new RefreshToken(tokenId, userId, tokenHash, expiresAt);

        assertThat(token.isValid()).isTrue();
    }

    @Test
    void testRefreshTokenIsInvalidWhenExpired() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String tokenHash = "hashed_token";
        Instant expiresAt = Instant.now().minusSeconds(1);

        RefreshToken token = new RefreshToken(tokenId, userId, tokenHash, expiresAt);

        assertThat(token.isValid()).isFalse();
    }

    @Test
    void testRefreshTokenIsInvalidWhenRevoked() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String tokenHash = "hashed_token";
        Instant expiresAt = Instant.now().plusSeconds(604800);

        RefreshToken token = new RefreshToken(tokenId, userId, tokenHash, expiresAt);
        token.setRevokedAt(Instant.now());

        assertThat(token.isValid()).isFalse();
    }
}
