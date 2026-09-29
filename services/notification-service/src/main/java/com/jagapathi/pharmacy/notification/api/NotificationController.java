package com.jagapathi.pharmacy.notification.api;

import com.jagapathi.pharmacy.notification.application.NotificationService;
import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationResponse>> listNotifications(
            Pageable pageable,
            Authentication authentication) {
        UUID userId = getUserIdFromJwt(authentication);
        Page<NotificationResponse> notifications = notificationService.getNotificationHistory(userId, pageable);
        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = getUserIdFromJwt(authentication);
        notificationService.markAsRead(id, userId);
        NotificationResponse response = notificationService.getNotification(id, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TemplateResponse>> listTemplates() {
        List<TemplateResponse> templates = notificationService.getAllTemplates();
        return ResponseEntity.ok(templates);
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<?> handleNotificationNotFound(NotificationNotFoundException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<?> handleUnauthorizedAccess(UnauthorizedAccessException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<?> handleTemplateNotFound(TemplateNotFoundException e) {
        return ResponseEntity.notFound().build();
    }

    private UUID getUserIdFromJwt(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        // auth-service issues the user's ID as the standard "sub" claim, not a custom
        // "userId" claim (see customer-service's CustomerController.extractUserId for the
        // same established pattern elsewhere in the codebase).
        String userIdStr = jwt.getClaimAsString("sub");
        return UUID.fromString(userIdStr);
    }
}
