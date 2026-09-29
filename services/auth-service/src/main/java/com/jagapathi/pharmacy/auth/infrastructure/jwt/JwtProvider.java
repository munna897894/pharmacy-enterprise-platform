package com.jagapathi.pharmacy.auth.infrastructure.jwt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jagapathi.pharmacy.auth.application.exception.InvalidTokenException;
import com.jagapathi.pharmacy.auth.domain.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@Component
public class JwtProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtProvider.class);

    private static final long ACCESS_TOKEN_EXPIRY_SECONDS = 900; // 15 minutes
    private static final long REFRESH_TOKEN_EXPIRY_SECONDS = 604800; // 7 days
    private static final String KEY_ID = "auth-service-key";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String issuer;
    private final String audience;

    public JwtProvider(@Value("${jwt.private-key-path:}") String privateKeyPath,
                       @Value("${jwt.public-key-path:}") String publicKeyPath,
                       @Value("${jwt.issuer:http://auth-service:8081}") String issuer,
                       @Value("${jwt.audience:pharmacy-api}") String audience) {
        this.privateKey = loadPrivateKey(privateKeyPath);
        this.publicKey = loadPublicKey(publicKeyPath);
        this.issuer = issuer;
        this.audience = audience;
    }

    private PrivateKey loadPrivateKey(String path) {
        try {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("JWT_PRIVATE_KEY_PATH not set");
            }
            String content = new String(Files.readAllBytes(Paths.get(path)))
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decodedKey = Base64.getDecoder().decode(content);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (IOException | java.security.NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException e) {
            logger.error("Failed to load private key from path: {}", path, e);
            throw new IllegalStateException("Failed to load private key", e);
        }
    }

    private PublicKey loadPublicKey(String path) {
        try {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("JWT_PUBLIC_KEY_PATH not set");
            }
            String content = new String(Files.readAllBytes(Paths.get(path)))
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decodedKey = Base64.getDecoder().decode(content);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (IOException | java.security.NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException e) {
            logger.error("Failed to load public key from path: {}", path, e);
            throw new IllegalStateException("Failed to load public key", e);
        }
    }

    public String generateAccessToken(UUID userId, String username, Set<Role> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ACCESS_TOKEN_EXPIRY_SECONDS);

        return Jwts.builder()
                .header()
                .add("alg", "RS256")
                .add("typ", "JWT")
                .add("kid", KEY_ID)
                .and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(userId.toString())
                .claim("username", username)
                .claim("roles", roles.stream().map(Role::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(REFRESH_TOKEN_EXPIRY_SECONDS);

        return Jwts.builder()
                .header()
                .add("alg", "RS256")
                .add("typ", "JWT")
                .add("kid", KEY_ID)
                .and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey)
                .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("JWT validation failed: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token", e);
        }
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = validateToken(token);
        return UUID.fromString(claims.getSubject());
    }

    public long getAccessTokenExpirySeconds() {
        return ACCESS_TOKEN_EXPIRY_SECONDS;
    }

    public long getRefreshTokenExpirySeconds() {
        return REFRESH_TOKEN_EXPIRY_SECONDS;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getJwksJson() {
        RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + KEY_ID + "\",\"n\":\""
                + base64UrlUnsigned(rsaPublicKey.getModulus())
                + "\",\"e\":\""
                + base64UrlUnsigned(rsaPublicKey.getPublicExponent())
                + "\"}]}";
    }

    private String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
