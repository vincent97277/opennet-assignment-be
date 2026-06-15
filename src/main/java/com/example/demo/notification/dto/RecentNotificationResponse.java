package com.example.demo.notification.dto;

import com.example.demo.notification.NotificationType;

import java.time.Instant;

public record RecentNotificationResponse(
        Long id,
        NotificationType type,
        String recipient,
        String subject,
        Instant createdAt
) {
}
