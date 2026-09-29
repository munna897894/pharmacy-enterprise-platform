package com.jagapathi.pharmacy.externalmock.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1/mock")
public class FailureModeController {

    public enum Mode { SUCCESS, REJECT, DELAY, ERROR }

    private final AtomicReference<Mode> prescriptionMode = new AtomicReference<>(Mode.SUCCESS);
    private final AtomicReference<Mode> paymentMode = new AtomicReference<>(Mode.SUCCESS);
    private final AtomicReference<Duration> delay = new AtomicReference<>(Duration.ZERO);

    @PutMapping("/prescription")
    public ResponseEntity<Map<String, String>> setPrescriptionMode(@RequestParam Mode mode, @RequestParam(required = false) Long delayMs) {
        prescriptionMode.set(mode);
        if (delayMs != null) {
            delay.set(Duration.ofMillis(delayMs));
        }
        return ResponseEntity.ok(Map.of("mode", mode.name()));
    }

    @PutMapping("/payment")
    public ResponseEntity<Map<String, String>> setPaymentMode(@RequestParam Mode mode, @RequestParam(required = false) Long delayMs) {
        paymentMode.set(mode);
        if (delayMs != null) {
            delay.set(Duration.ofMillis(delayMs));
        }
        return ResponseEntity.ok(Map.of("mode", mode.name()));
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        prescriptionMode.set(Mode.SUCCESS);
        paymentMode.set(Mode.SUCCESS);
        delay.set(Duration.ZERO);
        return ResponseEntity.noContent().build();
    }

    /**
     * Mock payment gateway used by payment-service's PaymentGatewayClient.
     * REJECT is a business decline (HTTP 200, success=false) so the caller maps
     * it to PaymentStatus.FAILED without an exception. ERROR/DELAY (past the
     * caller's read timeout) surface as RestClientException -> retryable
     * GatewayException for circuit-breaker/timeout scenarios.
     */
    @PostMapping("/process-payment")
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> request) {
        Mode mode = paymentMode.get();
        if (!delay.get().isZero()) {
            try { Thread.sleep(delay.get().toMillis()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        String reference = String.valueOf(request.getOrDefault("reference", "unknown"));
        return switch (mode) {
            case SUCCESS, DELAY -> ResponseEntity.ok(Map.of(
                "success", true,
                "transactionId", "MOCK-TXN-" + reference,
                "message", "approved"
            ));
            case REJECT -> ResponseEntity.ok(Map.of(
                "success", false,
                "transactionId", "",
                "message", "declined by mock gateway"
            ));
            case ERROR -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "transactionId", "",
                "message", "mock gateway error"
            ));
        };
    }

    @GetMapping("/prescription")
    public ResponseEntity<Map<String, Object>> prescriptionResponse() {
        return respond(prescriptionMode.get(), "prescription rejected by lab");
    }

    @GetMapping("/payment")
    public ResponseEntity<Map<String, Object>> paymentResponse() {
        return respond(paymentMode.get(), "payment rejected by lab");
    }

    private ResponseEntity<Map<String, Object>> respond(Mode mode, String rejectionMessage) {
        if (!delay.get().isZero()) {
            try { Thread.sleep(delay.get().toMillis()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        return switch (mode) {
            case SUCCESS -> ResponseEntity.ok(Map.of("status", "SUCCESS"));
            case REJECT -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "REJECTED", "reason", rejectionMessage));
            case DELAY -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DELAYED"));
            case ERROR -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "ERROR"));
        };
    }
}
