package com.example.demo.notification;

import com.example.demo.notification.dto.CreateNotificationRequest;
import com.example.demo.notification.dto.NotificationMessage;
import com.example.demo.notification.dto.NotificationResponse;
import com.example.demo.notification.dto.RecentNotificationResponse;
import com.example.demo.notification.dto.UpdateNotificationRequest;
import com.example.demo.notification.exception.NotificationDeliveryException;
import com.example.demo.notification.exception.NotificationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationCacheService notificationCacheService;

    @Mock
    private NotificationMessagePublisher notificationMessagePublisher;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                notificationCacheService,
                notificationMessagePublisher
        );
    }

    @Test
    void createPersistsPublishesAndCachesNotification() {
        Notification saved = notification(123L, NotificationType.EMAIL, "Welcome!", "Thanks");
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenReturn(saved);

        NotificationResponse response = notificationService.create(new CreateNotificationRequest(
                NotificationType.EMAIL,
                "user@example.com",
                "Welcome!",
                "Thanks"
        ));

        assertThat(response.id()).isEqualTo(123L);
        assertThat(response.type()).isEqualTo(NotificationType.EMAIL);
        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationMessagePublisher).publish(messageCaptor.capture());
        assertThat(messageCaptor.getValue().id()).isEqualTo(123L);
        verify(notificationCacheService).put(response);
        verify(notificationCacheService).addRecent(response);
    }

    @Test
    void createDoesNotCacheWhenRocketMqPublishFails() {
        Notification saved = notification(123L, NotificationType.SMS, "Welcome!", "Thanks");
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenReturn(saved);
        doThrow(new NotificationDeliveryException("Unable to publish notification message"))
                .when(notificationMessagePublisher)
                .publish(any(NotificationMessage.class));

        assertThatThrownBy(() -> notificationService.create(new CreateNotificationRequest(
                NotificationType.SMS,
                "+15551234567",
                "Welcome!",
                "Thanks"
        ))).isInstanceOf(NotificationDeliveryException.class);

        verify(notificationCacheService, never()).put(any(NotificationResponse.class));
        verify(notificationCacheService, never()).addRecent(any(NotificationResponse.class));
    }

    @Test
    void getByIdReturnsCachedNotificationWithoutDatabaseLookup() {
        NotificationResponse cached = response(123L, NotificationType.EMAIL, "Welcome!", "Thanks");
        when(notificationCacheService.findById(123L)).thenReturn(Optional.of(cached));

        NotificationResponse response = notificationService.getById(123L);

        assertThat(response).isEqualTo(cached);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void getByIdBackfillsCacheOnDatabaseHit() {
        Notification notification = notification(123L, NotificationType.EMAIL, "Welcome!", "Thanks");
        when(notificationCacheService.findById(123L)).thenReturn(Optional.empty());
        when(notificationRepository.findById(123L)).thenReturn(Optional.of(notification));

        NotificationResponse response = notificationService.getById(123L);

        assertThat(response.id()).isEqualTo(123L);
        verify(notificationCacheService).put(response);
    }

    @Test
    void getByIdThrowsNotFoundWhenNotificationDoesNotExist() {
        when(notificationCacheService.findById(404L)).thenReturn(Optional.empty());
        when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getById(404L))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void getRecentReturnsCachedRecentNotifications() {
        List<RecentNotificationResponse> cached = List.of(new RecentNotificationResponse(
                123L,
                NotificationType.EMAIL,
                "user@example.com",
                "Welcome!",
                Instant.parse("2025-07-15T12:01:02Z")
        ));
        when(notificationCacheService.findRecent()).thenReturn(cached);

        List<RecentNotificationResponse> recent = notificationService.getRecent();

        assertThat(recent).isEqualTo(cached);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void getRecentFallsBackToDatabaseAndRebuildsCache() {
        Notification notification = notification(123L, NotificationType.EMAIL, "Welcome!", "Thanks");
        when(notificationCacheService.findRecent()).thenReturn(List.of());
        when(notificationRepository.findTop10ByOrderByCreatedAtDescIdDesc()).thenReturn(List.of(notification));

        List<RecentNotificationResponse> recent = notificationService.getRecent();

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).id()).isEqualTo(123L);
        verify(notificationCacheService).replaceRecent(List.of(NotificationService.toResponse(notification)));
    }

    @Test
    void updateChangesSubjectAndContentAndInvalidatesRecentCache() {
        Notification notification = notification(123L, NotificationType.EMAIL, "Old", "Old content");
        when(notificationRepository.findById(123L)).thenReturn(Optional.of(notification));

        NotificationResponse response = notificationService.update(
                123L,
                new UpdateNotificationRequest("Updated", "Updated content")
        );

        assertThat(response.subject()).isEqualTo("Updated");
        assertThat(response.content()).isEqualTo("Updated content");
        verify(notificationCacheService).put(response);
        verify(notificationCacheService).evictRecent();
    }

    @Test
    void updateThrowsNotFoundWhenMissing() {
        when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.update(
                404L,
                new UpdateNotificationRequest("Updated", "Updated content")
        )).isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void deleteRemovesNotificationAndCacheEntries() {
        Notification notification = notification(123L, NotificationType.EMAIL, "Welcome!", "Thanks");
        when(notificationRepository.findById(123L)).thenReturn(Optional.of(notification));

        notificationService.delete(123L);

        verify(notificationRepository).delete(notification);
        verify(notificationRepository).flush();
        verify(notificationCacheService).evict(123L);
        verify(notificationCacheService).evictRecent();
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.delete(404L))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    private Notification notification(Long id, NotificationType type, String subject, String content) {
        Notification notification = new Notification(type, "user@example.com", subject, content);
        notification.setId(id);
        notification.setCreatedAt(Instant.parse("2025-07-15T12:01:02Z"));
        notification.setUpdatedAt(Instant.parse("2025-07-15T12:01:02Z"));
        return notification;
    }

    private NotificationResponse response(Long id, NotificationType type, String subject, String content) {
        return new NotificationResponse(
                id,
                type,
                "user@example.com",
                subject,
                content,
                Instant.parse("2025-07-15T12:01:02Z"),
                Instant.parse("2025-07-15T12:01:02Z")
        );
    }
}
