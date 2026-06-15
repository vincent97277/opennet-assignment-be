package com.example.demo.notification.dto;

import com.example.demo.notification.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateNotificationRequest(
        @NotNull NotificationType type,
        @NotBlank String recipient,
        String subject,
        @NotBlank String content
) {
}
