package com.jagapathi.pharmacy.gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import io.jsonwebtoken.Jwts;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;

    @Mock
    private JwtService jwtService;

    private MockChain chain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        this.filter = new JwtAuthenticationFilter(jwtService);
        this.chain = new MockChain();
    }

    @Test
    void shouldAllowAccessToPublicAuthEndpoints() {
        ServerWebExchange exchange = createExchangeForPath("/api/v1/auth/login");

        filter.filter(exchange, chain).block();

        // Should proceed to chain without requiring JWT
        assertTrue(chain.filterCalled);
    }

    @Test
    void shouldRejectPrivateEndpointWithoutAuthorizationHeader() {
        ServerWebExchange exchange = createExchangeForPath("/api/v1/medications");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldRejectPrivateEndpointWithMalformedAuthorizationHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/medications")
            .header(HttpHeaders.AUTHORIZATION, "InvalidFormat")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldAllow401ForPublicJwksEndpoint() {
        ServerWebExchange exchange = createExchangeForPath("/api/v1/auth/.well-known/jwks.json");

        filter.filter(exchange, chain).block();

        assertTrue(chain.filterCalled);
    }

    @Test
    void shouldReturnOrder() {
        assertEquals(-200, filter.getOrder());
    }

    @Test
    void shouldAcceptListRolesClaimAndForwardCommaJoinedRolesHeader() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        String token = Jwts.builder()
            .header()
            .add("alg", "RS256")
            .add("typ", "JWT")
            .add("kid", "test-key")
            .and()
            .subject("11111111-1111-1111-1111-111111111111")
            .claim("username", "jane")
            .claim("roles", List.of("PHARMACIST", "ADMIN"))
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(900)))
            .signWith(keyPair.getPrivate())
            .compact();

        when(jwtService.getPublicKey("test-key")).thenReturn(Mono.just(keyPair.getPublic()));

        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/medications")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertTrue(chain.filterCalled);
        assertEquals("PHARMACIST,ADMIN", chain.capturedRequest.getHeaders().getFirst("X-User-Roles"));
        assertEquals("11111111-1111-1111-1111-111111111111", chain.capturedRequest.getHeaders().getFirst("X-User-Id"));
    }

    private ServerWebExchange createExchangeForPath(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        return MockServerWebExchange.from(request);
    }

    private static class MockChain implements org.springframework.cloud.gateway.filter.GatewayFilterChain {
        boolean filterCalled = false;
        org.springframework.http.server.reactive.ServerHttpRequest capturedRequest;

        @Override
        public reactor.core.publisher.Mono<Void> filter(ServerWebExchange exchange) {
            filterCalled = true;
            capturedRequest = exchange.getRequest();
            return reactor.core.publisher.Mono.empty();
        }
    }
}
