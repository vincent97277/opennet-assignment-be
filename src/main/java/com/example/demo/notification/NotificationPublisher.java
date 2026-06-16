package com.example.demo.notification;

import com.example.demo.notification.dto.NotificationMessage;

public interface NotificationPublisher {

    void publish(NotificationMessage message);
}
