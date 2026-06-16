package com.example.demo.notification.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum NotificationType {
    EMAIL,
    SMS;

    @JsonCreator
    public static NotificationType fromJson(String value) {
        if (value == null) {
            return null;
        }
        for (NotificationType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("type must be email or sms");
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
