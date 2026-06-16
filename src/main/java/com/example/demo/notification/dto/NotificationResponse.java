package com.example.demo.notification.dto;

import com.example.demo.notification.Notification;
import com.example.demo.notification.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String recipient,
        String subject,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getRecipient(),
                notification.getSubject(),
                notification.getContent(),
                notification.getCreatedAt(),
                notification.getUpdatedAt()
        );
    }
}
