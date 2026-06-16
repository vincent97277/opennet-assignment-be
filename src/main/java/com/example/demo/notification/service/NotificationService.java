package com.example.demo.notification.service;

import com.example.demo.notification.domain.Notification;
import com.example.demo.notification.dto.CreateNotificationRequest;
import com.example.demo.notification.dto.NotificationMessage;
import com.example.demo.notification.dto.NotificationResponse;
import com.example.demo.notification.dto.RecentNotificationResponse;
import com.example.demo.notification.dto.UpdateNotificationRequest;
import com.example.demo.notification.exception.NotificationNotFoundException;
import com.example.demo.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationCacheService notificationCacheService;
    private final NotificationMessagePublisher notificationMessagePublisher;

    @Transactional
    public NotificationResponse create(CreateNotificationRequest request) {
        Notification notification = new Notification(
                request.type(),
                request.recipient(),
                request.subject(),
                request.content()
        );
        Notification saved = notificationRepository.saveAndFlush(notification);
        NotificationResponse response = NotificationResponse.from(saved);

        notificationMessagePublisher.publish(NotificationMessage.from(response));
        notificationCacheService.cacheCreated(response);

        return response;
    }

    @Transactional(readOnly = true)
    public NotificationResponse getById(Long id) {
        return notificationCacheService.findById(id)
                .orElseGet(() -> {
                    Notification notification = notificationRepository.findById(id)
                            .orElseThrow(() -> new NotificationNotFoundException(id));
                    NotificationResponse response = NotificationResponse.from(notification);
                    notificationCacheService.put(response);
                    return response;
                });
    }

    @Transactional(readOnly = true)
    public List<RecentNotificationResponse> getRecent() {
        List<RecentNotificationResponse> cached = notificationCacheService.findRecent();
        if (!cached.isEmpty()) {
            return cached;
        }

        List<NotificationResponse> responses = notificationRepository.findTop10ByOrderByCreatedAtDescIdDesc()
                .stream()
                .map(NotificationResponse::from)
                .toList();
        notificationCacheService.replaceRecent(responses);
        return responses.stream()
                .map(RecentNotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse update(Long id, UpdateNotificationRequest request) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        notification.update(request.subject(), request.content());
        NotificationResponse response = NotificationResponse.from(notification);

        notificationCacheService.put(response);
        notificationCacheService.evictRecent();

        return response;
    }

    @Transactional
    public void delete(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        notificationRepository.delete(notification);
        notificationRepository.flush();
        notificationCacheService.evict(id);
        notificationCacheService.evictRecent();
    }
}
