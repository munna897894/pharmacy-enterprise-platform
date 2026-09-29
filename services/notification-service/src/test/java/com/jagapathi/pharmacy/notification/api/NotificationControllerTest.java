package com.jagapathi.pharmacy.notification.api;

import com.jagapathi.pharmacy.notification.application.NotificationService;
import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.Notification;
import com.jagapathi.pharmacy.notification.domain.NotificationStatus;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import com.jagapathi.pharmacy.notification.infrastructure.TestSecurityConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfiguration.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String JWT_TOKEN = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwidXNlcklkIjoiNTUwZTg0MDAtZTI5Yi00MWQ0LWE3MTYtNDQ2NjU1NDQwMDAwIiwicm9sZXMiOlsiQ1VTVE9NRVIiXSwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjk5OTk5OTk5OTksImlzcyI6Imh0dHA6Ly9hdXRoLXNlcnZpY2U6ODA4MCJ9.signature";

    @Test
    void testListNotifications() throws Exception {
        UUID customerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        Notification notification = new Notification(
                UUID.randomUUID(), customerId, NotificationType.ORDER_CONFIRMATION,
                Channel.EMAIL, "test@example.com", "Subject", "Message"
        );
        notification.markAsSent();

        Page<NotificationResponse> page = new PageImpl<>(
                Arrays.asList(new NotificationResponse(
                        notification.getId(), notification.getCustomerId(), notification.getType(),
                        notification.getChannel(), notification.getRecipient(), notification.getSubject(),
                        notification.getMessage(), notification.getStatus(), notification.getSentAt(),
                        notification.getReadAt(), notification.getCreatedAt(), notification.getUpdatedAt()
                )),
                PageRequest.of(0, 10), 1
        );

        when(notificationService.getNotificationHistory(eq(customerId), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/notifications")
                .header("Authorization", JWT_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void testMarkAsRead() throws Exception {
        UUID notificationId = UUID.randomUUID();
        UUID customerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        Notification notification = new Notification(
                notificationId, customerId, NotificationType.ORDER_CONFIRMATION,
                Channel.EMAIL, "test@example.com", "Subject", "Message"
        );
        notification.markAsSent();
        notification.markAsRead();

        NotificationResponse response = new NotificationResponse(
                notification.getId(), notification.getCustomerId(), notification.getType(),
                notification.getChannel(), notification.getRecipient(), notification.getSubject(),
                notification.getMessage(), notification.getStatus(), notification.getSentAt(),
                notification.getReadAt(), notification.getCreatedAt(), notification.getUpdatedAt()
        );

        when(notificationService.getNotification(notificationId, customerId))
                .thenReturn(response);
        doNothing().when(notificationService).markAsRead(notificationId, customerId);

        mockMvc.perform(put("/api/v1/notifications/{id}/read", notificationId)
                .header("Authorization", JWT_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testNotificationNotFound() throws Exception {
        UUID notificationId = UUID.randomUUID();
        UUID customerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        when(notificationService.getNotification(notificationId, customerId))
                .thenThrow(new NotificationNotFoundException("Not found"));

        mockMvc.perform(put("/api/v1/notifications/{id}/read", notificationId)
                .header("Authorization", JWT_TOKEN))
                .andExpect(status().isNotFound());
    }
}

