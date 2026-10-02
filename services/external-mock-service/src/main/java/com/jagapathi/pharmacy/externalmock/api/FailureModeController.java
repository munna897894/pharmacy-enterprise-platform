package com.jagapathi.pharmacy.externalmock.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1/mock")
public class FailureModeController {

    private static final Logger log = LoggerFactory.getLogger(FailureModeController.class);
    private static final java.util.regex.Pattern PAYMENT_REFERENCE =
        java.util.regex.Pattern.compile("^PAY-([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

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
        String reference = request.get("reference") instanceof String value ? value : "unknown";
        ResponseEntity<Map<String, Object>> response = switch (mode) {
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
        Object successValue = response.getBody() == null ? null : response.getBody().get("success");
        String outcome = Boolean.TRUE.equals(successValue)
            ? "approved"
            : response.getStatusCode().is2xxSuccessful() ? "declined" : "error";
        UUID paymentId = paymentIdFromReference(reference);
        var lifecycleLog = log.atLevel("approved".equals(outcome)
            ? org.slf4j.event.Level.INFO : org.slf4j.event.Level.WARN)
            .addKeyValue("eventName", "external.payment.gateway_response")
            .addKeyValue("mode", mode)
            .addKeyValue("httpStatus", response.getStatusCode().value())
            .addKeyValue("outcome", outcome);
        if (paymentId != null) {
            lifecycleLog.addKeyValue("paymentId", paymentId);
        }
        lifecycleLog.log("External payment gateway lifecycle event");
        return response;
    }

    private UUID paymentIdFromReference(String reference) {
        var matcher = PAYMENT_REFERENCE.matcher(reference);
        if (!matcher.matches()) {
            return null;
        }
        return UUID.fromString(matcher.group(1));
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
