package com.jagapathi.pharmacy.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private final ObjectMapper objectMapper;

    public FallbackController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostMapping("/**")
    @GetMapping("/**")
    @PutMapping("/**")
    @PatchMapping("/**")
    public Mono<ResponseEntity<Map<String, Object>>> fallback(ServerWebExchange exchange) {
        String correlationId = exchange.getAttribute("correlationId");

        Map<String, Object> response = new HashMap<>();
        response.put("type", "https://example.com/errors/service-unavailable");
        response.put("title", "Service Unavailable");
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("detail", "Downstream service is temporarily unavailable");
        response.put("instance", exchange.getRequest().getPath().value());
        response.put("correlationId", correlationId);
        response.put("timestamp", Instant.now().toString());

        return Mono.just(ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(response));
    }
}
