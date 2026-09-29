package com.jagapathi.pharmacy.notification.application;

import com.jagapathi.pharmacy.notification.api.NotificationResponse;
import com.jagapathi.pharmacy.notification.api.UnauthorizedAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Notification rows store the customer-service customer ID, while JWT {@code sub} is the auth user ID.
 * Customers are authorized by asking customer-service whether the caller owns that customer profile;
 * staff roles may read any customer's notifications.
 */
@Service
public class NotificationAccessService {

    private final NotificationService notificationService;
    private final CustomerOwnershipVerifier ownershipVerifier;

    public NotificationAccessService(NotificationService notificationService,
                                     CustomerOwnershipVerifier ownershipVerifier) {
        this.notificationService = notificationService;
        this.ownershipVerifier = ownershipVerifier;
    }

    public Page<NotificationResponse> listForCustomer(UUID customerId, Pageable pageable, Jwt callerJwt, boolean staff) {
        requireAccess(customerId, callerJwt, staff);
        return notificationService.getNotificationHistory(customerId, pageable);
    }

    public NotificationResponse get(UUID notificationId, Jwt callerJwt, boolean staff) {
        requireAccess(notificationService.getNotificationOwner(notificationId), callerJwt, staff);
        return notificationService.getNotification(notificationId);
    }

    public NotificationResponse markAsRead(UUID notificationId, Jwt callerJwt, boolean staff) {
        requireAccess(notificationService.getNotificationOwner(notificationId), callerJwt, staff);
        notificationService.markAsRead(notificationId);
        return notificationService.getNotification(notificationId);
    }

    private void requireAccess(UUID customerId, Jwt callerJwt, boolean staff) {
        if (!staff && !ownershipVerifier.isOwner(customerId, callerJwt)) {
            throw new UnauthorizedAccessException("Cannot access notifications for this customer");
        }
    }
}
