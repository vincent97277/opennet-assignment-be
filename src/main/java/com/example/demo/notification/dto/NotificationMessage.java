package com.example.demo.notification.dto;

import com.example.demo.notification.domain.NotificationType;

import java.time.Instant;

public record NotificationMessage(
        Long id,
        NotificationType type,
        String recipient,
        String subject,
        String content,
        Instant createdAt
) {
    public static NotificationMessage from(NotificationResponse response) {
        return new NotificationMessage(
                response.id(),
                response.type(),
                response.recipient(),
                response.subject(),
                response.content(),
                response.createdAt()
        );
    }
}
