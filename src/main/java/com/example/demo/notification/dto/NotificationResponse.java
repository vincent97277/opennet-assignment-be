package com.example.demo.notification.dto;

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
}
