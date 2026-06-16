package com.example.demo.notification;

import com.example.demo.notification.dto.CreateNotificationRequest;
import com.example.demo.notification.dto.NotificationResponse;
import com.example.demo.notification.dto.RecentNotificationResponse;
import com.example.demo.notification.dto.UpdateNotificationRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

public final class NotificationFixtures {

    public static final Long NOTIFICATION_ID = 123L;
    public static final Long SECOND_NOTIFICATION_ID = 456L;
    public static final Long MISSING_NOTIFICATION_ID = 404L;

    public static final NotificationType EMAIL_TYPE = NotificationType.EMAIL;
    public static final NotificationType SMS_TYPE = NotificationType.SMS;

    public static final String EMAIL_RECIPIENT = "user@example.com";
    public static final String SMS_RECIPIENT = "+15551234567";
    public static final String SUBJECT = "Welcome!";
    public static final String CONTENT = "Thanks";
    public static final String OLD_SUBJECT = "Old";
    public static final String OLD_CONTENT = "Old content";
    public static final String UPDATED_SUBJECT = "Updated subject";
    public static final String UPDATED_CONTENT = "Updated content";
    public static final String DELIVERY_FAILURE_MESSAGE = "Unable to publish notification message";

    public static final Instant CREATED_AT = Instant.parse("2025-07-15T12:01:02Z");
    public static final Instant UPDATED_AT = Instant.parse("2025-07-15T12:03:04Z");

    private NotificationFixtures() {
    }

    public static CreateNotificationRequest createEmailRequest() {
        return new CreateNotificationRequest(EMAIL_TYPE, EMAIL_RECIPIENT, SUBJECT, CONTENT);
    }

    public static CreateNotificationRequest createSmsRequest() {
        return new CreateNotificationRequest(SMS_TYPE, SMS_RECIPIENT, SUBJECT, CONTENT);
    }

    public static UpdateNotificationRequest updateRequest() {
        return new UpdateNotificationRequest(UPDATED_SUBJECT, UPDATED_CONTENT);
    }

    public static Notification notification(Long id, NotificationType type, String subject, String content) {
        Notification notification = new Notification(type, EMAIL_RECIPIENT, subject, content);
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(notification, "updatedAt", UPDATED_AT);
        return notification;
    }

    public static NotificationResponse response(Long id, NotificationType type, String subject, String content) {
        return new NotificationResponse(id, type, EMAIL_RECIPIENT, subject, content, CREATED_AT, UPDATED_AT);
    }

    public static NotificationResponse response(Long id) {
        return response(id, EMAIL_TYPE, SUBJECT, CONTENT);
    }

    public static RecentNotificationResponse recentResponse(Long id) {
        return new RecentNotificationResponse(id, EMAIL_TYPE, EMAIL_RECIPIENT, SUBJECT, CREATED_AT);
    }
}
