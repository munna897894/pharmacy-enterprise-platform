package com.jagapathi.pharmacy.notification.api;

import com.jagapathi.pharmacy.notification.application.CustomerOwnershipLookupException;
import com.jagapathi.pharmacy.notification.application.CustomerOwnershipVerifier;
import com.jagapathi.pharmacy.notification.application.NotificationService;
import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.NotificationStatus;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import com.jagapathi.pharmacy.notification.infrastructure.TestSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfiguration.class)
class NotificationControllerTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID CUSTOMER_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final UUID NOTIFICATION_ID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private CustomerOwnershipVerifier ownershipVerifier;

    @Test
    void customerListsOwnNotificationsWhenCustomerServiceConfirmsOwnership() throws Exception {
        when(ownershipVerifier.isOwner(eq(CUSTOMER_ID), any())).thenReturn(true);
        when(notificationService.getNotificationHistory(eq(CUSTOMER_ID), any()))
            .thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/notifications").param("customerId", CUSTOMER_ID.toString())
                .with(customerJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].customerId").value(CUSTOMER_ID.toString()));
    }

    @Test
    void customerCannotListAnotherCustomersNotifications() throws Exception {
        when(ownershipVerifier.isOwner(eq(CUSTOMER_ID), any())).thenReturn(false);

        mockMvc.perform(get("/api/v1/notifications").param("customerId", CUSTOMER_ID.toString())
                .with(customerJwt()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Forbidden"));

        verify(notificationService, never()).getNotificationHistory(any(), any());
    }

    @Test
    void staffListsAnyCustomerWithoutOwnershipLookup() throws Exception {
        when(notificationService.getNotificationHistory(eq(CUSTOMER_ID), any()))
            .thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/notifications").param("customerId", CUSTOMER_ID.toString())
                .with(jwt().jwt(j -> j.subject(AUTH_USER_ID.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_PHARMACIST"))))
            .andExpect(status().isOk());

        verifyNoInteractions(ownershipVerifier);
    }

    @Test
    void listRequiresCustomerId() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").with(customerJwt()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listRejectsMalformedCustomerId() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("customerId", "not-a-uuid").with(customerJwt()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void ownershipLookupFailureReturnsSanitized503() throws Exception {
        when(ownershipVerifier.isOwner(eq(CUSTOMER_ID), any()))
            .thenThrow(new CustomerOwnershipLookupException(new RuntimeException("connection refused")));

        mockMvc.perform(get("/api/v1/notifications").param("customerId", CUSTOMER_ID.toString())
                .with(customerJwt()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail").value("Notification access cannot be verified right now."));
    }

    @Test
    void customerReadsOwnNotificationById() throws Exception {
        when(notificationService.getNotificationOwner(NOTIFICATION_ID)).thenReturn(CUSTOMER_ID);
        when(ownershipVerifier.isOwner(eq(CUSTOMER_ID), any())).thenReturn(true);
        when(notificationService.getNotification(NOTIFICATION_ID)).thenReturn(response());

        mockMvc.perform(get("/api/v1/notifications/{id}", NOTIFICATION_ID).with(customerJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(NOTIFICATION_ID.toString()));
    }

    @Test
    void customerMarksOwnNotificationAsRead() throws Exception {
        when(notificationService.getNotificationOwner(NOTIFICATION_ID)).thenReturn(CUSTOMER_ID);
        when(ownershipVerifier.isOwner(eq(CUSTOMER_ID), any())).thenReturn(true);
        when(notificationService.getNotification(NOTIFICATION_ID)).thenReturn(response());

        mockMvc.perform(put("/api/v1/notifications/{id}/read", NOTIFICATION_ID).with(customerJwt()))
            .andExpect(status().isOk());

        verify(notificationService).markAsRead(NOTIFICATION_ID);
    }

    @Test
    void customerCannotMarkAnotherCustomersNotification() throws Exception {
        when(notificationService.getNotificationOwner(NOTIFICATION_ID)).thenReturn(CUSTOMER_ID);
        when(ownershipVerifier.isOwner(eq(CUSTOMER_ID), any())).thenReturn(false);

        mockMvc.perform(put("/api/v1/notifications/{id}/read", NOTIFICATION_ID).with(customerJwt()))
            .andExpect(status().isForbidden());

        verify(notificationService, never()).markAsRead(any());
    }

    @Test
    void unknownNotificationReturns404() throws Exception {
        when(notificationService.getNotificationOwner(NOTIFICATION_ID))
            .thenThrow(new NotificationNotFoundException("Notification not found: " + NOTIFICATION_ID));

        mockMvc.perform(get("/api/v1/notifications/{id}", NOTIFICATION_ID).with(customerJwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("The requested notification does not exist."));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("customerId", CUSTOMER_ID.toString()))
            .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor customerJwt() {
        return jwt().jwt(j -> j.subject(AUTH_USER_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static NotificationResponse response() {
        Instant now = Instant.parse("2026-09-01T10:00:00Z");
        return new NotificationResponse(NOTIFICATION_ID, CUSTOMER_ID, null, NotificationType.ORDER_CONFIRMATION,
            Channel.EMAIL, "customer@example.test", "Subject", "Message", NotificationStatus.SENT,
            now, null, now, now);
    }
}
