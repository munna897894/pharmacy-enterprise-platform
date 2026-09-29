package com.jagapathi.pharmacy.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtService {
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    private final WebClient webClient;
    private final String jwksUri;
    private final ObjectMapper objectMapper;

    private volatile Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile long jwksCacheExpiry = 0;
    private static final long JWKS_CACHE_TTL_MILLIS = 3600_000; // 1 hour

    public JwtService(WebClient webClient, @Value("${auth-service.jwks-uri}") String jwksUri) {
        this.webClient = webClient;
        this.jwksUri = jwksUri;
        this.objectMapper = new ObjectMapper();
    }

    public Mono<PublicKey> getPublicKey(String kid) {
        if (isJwksCacheValid()) {
            PublicKey key = keyCache.get(kid);
            if (key != null) {
                return Mono.just(key);
            }
        }

        return refreshJwks()
            .flatMap(ignored -> {
                PublicKey key = keyCache.get(kid);
                if (key != null) {
                    return Mono.just(key);
                }
                return Mono.error(new Exception("Key not found: " + kid));
            });
    }

    private boolean isJwksCacheValid() {
        return System.currentTimeMillis() < jwksCacheExpiry;
    }

    private Mono<Void> refreshJwks() {
        return webClient.get()
            .uri(jwksUri)
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(response -> {
                try {
                    JsonNode root = objectMapper.readTree(response);
                    JsonNode keys = root.get("keys");
                    Map<String, PublicKey> newCache = new ConcurrentHashMap<>();

                    if (keys != null && keys.isArray()) {
                        for (JsonNode keyNode : keys) {
                            String kid = keyNode.get("kid").asText();
                            String kty = keyNode.get("kty").asText();

                            if ("RSA".equals(kty)) {
                                PublicKey publicKey = extractRsaPublicKey(keyNode);
                                newCache.put(kid, publicKey);
                            }
                        }
                    }

                    keyCache = newCache;
                    jwksCacheExpiry = System.currentTimeMillis() + JWKS_CACHE_TTL_MILLIS;
                    logger.info("JWKS refreshed, {} keys cached", newCache.size());
                    return Mono.empty();
                } catch (Exception e) {
                    logger.error("Error refreshing JWKS", e);
                    return Mono.error(e);
                }
            })
            .doOnError(e -> logger.error("Failed to fetch JWKS from {}", jwksUri, e))
            .then();
    }

    private PublicKey extractRsaPublicKey(JsonNode keyNode) throws Exception {
        String modulus = keyNode.get("n").asText();
        String exponent = keyNode.get("e").asText();

        byte[] modulusBytes = Base64.getUrlDecoder().decode(modulus);
        byte[] exponentBytes = Base64.getUrlDecoder().decode(exponent);

        java.math.BigInteger mod = new java.math.BigInteger(1, modulusBytes);
        java.math.BigInteger exp = new java.math.BigInteger(1, exponentBytes);

        java.security.spec.RSAPublicKeySpec publicKeySpec = new java.security.spec.RSAPublicKeySpec(mod, exp);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return keyFactory.generatePublic(publicKeySpec);
    }
}
