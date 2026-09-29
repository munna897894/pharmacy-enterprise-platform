package com.jagapathi.pharmacy.notification.infrastructure.persistence;

import com.jagapathi.pharmacy.notification.domain.NotificationTemplate;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import com.jagapathi.pharmacy.notification.domain.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByTypeAndChannelAndIsActiveTrue(NotificationType type, Channel channel);
    List<NotificationTemplate> findByIsActiveTrue();
}
