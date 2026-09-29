package com.jagapathi.pharmacy.gateway.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicHealthControllerTest {

    private final HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);
    private final PublicHealthController controller = new PublicHealthController(healthEndpoint);

    @Test
    void returnsOkWithoutDetailsWhenReady() {
        when(healthEndpoint.healthForPath("readiness"))
            .thenReturn(Health.up().withDetail("secret", "hidden").build());

        ResponseEntity<Map<String, String>> response = controller.health().block(Duration.ofSeconds(5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("status", "UP"));
    }

    @Test
    void returnsServiceUnavailableWhenNotReady() {
        when(healthEndpoint.healthForPath("readiness")).thenReturn(Health.outOfService().build());

        ResponseEntity<Map<String, String>> response = controller.health().block(Duration.ofSeconds(5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(Map.of("status", "OUT_OF_SERVICE"));
    }

    @Test
    void returnsServiceUnavailableWhenGroupMissing() {
        when(healthEndpoint.healthForPath("readiness")).thenReturn(null);

        ResponseEntity<Map<String, String>> response = controller.health().block(Duration.ofSeconds(5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
