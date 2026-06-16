package com.example.demo.notification;

import com.example.demo.notification.dto.NotificationMessage;
import com.example.demo.notification.dto.NotificationResponse;
import com.example.demo.notification.dto.RecentNotificationResponse;
import com.example.demo.notification.exception.NotificationDeliveryException;
import com.example.demo.notification.exception.NotificationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.example.demo.notification.NotificationFixtures.CONTENT;
import static com.example.demo.notification.NotificationFixtures.DELIVERY_FAILURE_MESSAGE;
import static com.example.demo.notification.NotificationFixtures.EMAIL_RECIPIENT;
import static com.example.demo.notification.NotificationFixtures.EMAIL_TYPE;
import static com.example.demo.notification.NotificationFixtures.MISSING_NOTIFICATION_ID;
import static com.example.demo.notification.NotificationFixtures.NOTIFICATION_ID;
import static com.example.demo.notification.NotificationFixtures.OLD_CONTENT;
import static com.example.demo.notification.NotificationFixtures.OLD_SUBJECT;
import static com.example.demo.notification.NotificationFixtures.SMS_TYPE;
import static com.example.demo.notification.NotificationFixtures.SUBJECT;
import static com.example.demo.notification.NotificationFixtures.UPDATED_CONTENT;
import static com.example.demo.notification.NotificationFixtures.UPDATED_SUBJECT;
import static com.example.demo.notification.NotificationFixtures.createEmailRequest;
import static com.example.demo.notification.NotificationFixtures.createSmsRequest;
import static com.example.demo.notification.NotificationFixtures.notification;
import static com.example.demo.notification.NotificationFixtures.recentResponse;
import static com.example.demo.notification.NotificationFixtures.response;
import static com.example.demo.notification.NotificationFixtures.updateRequest;
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
    private NotificationPublisher notificationPublisher;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                notificationCacheService,
                notificationPublisher
        );
    }

    @Test
    void createPersistsPublishesAndCachesNotification() {
        Notification saved = notification(NOTIFICATION_ID, EMAIL_TYPE, SUBJECT, CONTENT);
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenReturn(saved);

        NotificationResponse response = notificationService.create(createEmailRequest());

        assertThat(response).isEqualTo(NotificationResponse.from(saved));

        ArgumentCaptor<Notification> savedNotificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(savedNotificationCaptor.capture());
        assertThat(savedNotificationCaptor.getValue())
                .extracting(Notification::getType, Notification::getRecipient, Notification::getSubject, Notification::getContent)
                .containsExactly(EMAIL_TYPE, EMAIL_RECIPIENT, SUBJECT, CONTENT);

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationPublisher).publish(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isEqualTo(NotificationMessage.from(response));
        verify(notificationCacheService).cacheCreated(response);
    }

    @Test
    void createDoesNotCacheWhenRocketMqPublishFails() {
        Notification saved = notification(NOTIFICATION_ID, SMS_TYPE, SUBJECT, CONTENT);
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenReturn(saved);
        doThrow(new NotificationDeliveryException(DELIVERY_FAILURE_MESSAGE))
                .when(notificationPublisher)
                .publish(any(NotificationMessage.class));

        assertThatThrownBy(() -> notificationService.create(createSmsRequest()))
                .isInstanceOf(NotificationDeliveryException.class);

        verify(notificationCacheService, never()).cacheCreated(any(NotificationResponse.class));
    }

    @Test
    void getByIdReturnsCachedNotificationWithoutDatabaseLookup() {
        NotificationResponse cached = response(NOTIFICATION_ID, EMAIL_TYPE, SUBJECT, CONTENT);
        when(notificationCacheService.findById(NOTIFICATION_ID)).thenReturn(Optional.of(cached));

        NotificationResponse response = notificationService.getById(NOTIFICATION_ID);

        assertThat(response).isEqualTo(cached);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void getByIdBackfillsCacheOnDatabaseHit() {
        Notification notification = notification(NOTIFICATION_ID, EMAIL_TYPE, SUBJECT, CONTENT);
        when(notificationCacheService.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification));

        NotificationResponse response = notificationService.getById(NOTIFICATION_ID);

        assertThat(response).isEqualTo(NotificationResponse.from(notification));
        verify(notificationCacheService).put(NotificationResponse.from(notification));
    }

    @Test
    void getByIdThrowsNotFoundWhenNotificationDoesNotExist() {
        when(notificationCacheService.findById(MISSING_NOTIFICATION_ID)).thenReturn(Optional.empty());
        when(notificationRepository.findById(MISSING_NOTIFICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getById(MISSING_NOTIFICATION_ID))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void getRecentReturnsCachedRecentNotifications() {
        List<RecentNotificationResponse> cached = List.of(recentResponse(NOTIFICATION_ID));
        when(notificationCacheService.findRecent()).thenReturn(cached);

        List<RecentNotificationResponse> recent = notificationService.getRecent();

        assertThat(recent).isEqualTo(cached);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void getRecentFallsBackToDatabaseAndRebuildsCache() {
        Notification notification = notification(NOTIFICATION_ID, EMAIL_TYPE, SUBJECT, CONTENT);
        when(notificationCacheService.findRecent()).thenReturn(List.of());
        when(notificationRepository.findTop10ByOrderByCreatedAtDescIdDesc()).thenReturn(List.of(notification));

        List<RecentNotificationResponse> recent = notificationService.getRecent();

        assertThat(recent).containsExactly(RecentNotificationResponse.from(NotificationResponse.from(notification)));
        verify(notificationCacheService).replaceRecent(List.of(NotificationResponse.from(notification)));
    }

    @Test
    void updateChangesSubjectAndContentAndInvalidatesRecentCache() {
        Notification notification = notification(NOTIFICATION_ID, EMAIL_TYPE, OLD_SUBJECT, OLD_CONTENT);
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification));

        NotificationResponse response = notificationService.update(NOTIFICATION_ID, updateRequest());

        assertThat(response.subject()).isEqualTo(UPDATED_SUBJECT);
        assertThat(response.content()).isEqualTo(UPDATED_CONTENT);
        verify(notificationCacheService).put(response);
        verify(notificationCacheService).evictRecent();
    }

    @Test
    void updateThrowsNotFoundWhenMissing() {
        when(notificationRepository.findById(MISSING_NOTIFICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.update(MISSING_NOTIFICATION_ID, updateRequest()))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void deleteRemovesNotificationAndCacheEntries() {
        Notification notification = notification(NOTIFICATION_ID, EMAIL_TYPE, SUBJECT, CONTENT);
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification));

        notificationService.delete(NOTIFICATION_ID);

        verify(notificationRepository).delete(notification);
        verify(notificationRepository).flush();
        verify(notificationCacheService).evict(NOTIFICATION_ID);
        verify(notificationCacheService).evictRecent();
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        when(notificationRepository.findById(MISSING_NOTIFICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.delete(MISSING_NOTIFICATION_ID))
                .isInstanceOf(NotificationNotFoundException.class);
    }
}
