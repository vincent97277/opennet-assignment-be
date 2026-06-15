package com.example.demo.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateNotificationRequest(
        @NotBlank String subject,
        @NotBlank String content
) {
}
