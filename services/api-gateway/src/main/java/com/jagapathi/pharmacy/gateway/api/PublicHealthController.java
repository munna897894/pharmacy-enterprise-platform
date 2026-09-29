package com.jagapathi.pharmacy.gateway.api;

import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Detail-free readiness status on the public traffic port for load-balancer health checks.
 * Full actuator endpoints live on the internal management port.
 */
@RestController
public class PublicHealthController {

    private static final String READINESS_GROUP = "readiness";

    private final HealthEndpoint healthEndpoint;

    public PublicHealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/actuator/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.fromSupplier(() -> toResponse(healthEndpoint.healthForPath(READINESS_GROUP)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private static ResponseEntity<Map<String, String>> toResponse(HealthComponent health) {
        Status status = health == null ? Status.UNKNOWN : health.getStatus();
        HttpStatus httpStatus = Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(Map.of("status", status.getCode()));
    }
}
