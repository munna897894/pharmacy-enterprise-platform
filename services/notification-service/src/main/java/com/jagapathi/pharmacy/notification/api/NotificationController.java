package com.jagapathi.pharmacy.notification.api;

import com.jagapathi.pharmacy.notification.application.CustomerOwnershipLookupException;
import com.jagapathi.pharmacy.notification.application.NotificationAccessService;
import com.jagapathi.pharmacy.notification.application.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final Set<String> STAFF_AUTHORITIES =
        Set.of("ROLE_PHARMACIST", "ROLE_STORE_MANAGER", "ROLE_ADMIN");

    private final NotificationService notificationService;
    private final NotificationAccessService notificationAccessService;

    public NotificationController(NotificationService notificationService,
                                  NotificationAccessService notificationAccessService) {
        this.notificationService = notificationService;
        this.notificationAccessService = notificationAccessService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationResponse>> listNotifications(
            @RequestParam UUID customerId,
            Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(notificationAccessService.listForCustomer(
            customerId, pageable, jwt(authentication), isStaff(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponse> getNotification(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(notificationAccessService.get(id, jwt(authentication), isStaff(authentication)));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(notificationAccessService.markAsRead(id, jwt(authentication), isStaff(authentication)));
    }

    @GetMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TemplateResponse>> listTemplates() {
        return ResponseEntity.ok(notificationService.getAllTemplates());
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotificationNotFound() {
        return problem(HttpStatus.NOT_FOUND, "Notification not found", "The requested notification does not exist.");
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorizedAccess() {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "You cannot access notifications for this customer.");
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTemplateNotFound() {
        return problem(HttpStatus.NOT_FOUND, "Template not found", "The requested template does not exist.");
    }

    @ExceptionHandler(CustomerOwnershipLookupException.class)
    public ResponseEntity<ProblemDetail> handleOwnershipLookupFailure() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Customer ownership verification unavailable",
            "Notification access cannot be verified right now.");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return ResponseEntity.status(status).body(problemDetail);
    }

    private static Jwt jwt(Authentication authentication) {
        return (Jwt) authentication.getPrincipal();
    }

    private static boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(STAFF_AUTHORITIES::contains);
    }
}
