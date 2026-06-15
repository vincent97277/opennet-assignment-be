package com.example.demo.notification;

import com.example.demo.notification.dto.CreateNotificationRequest;
import com.example.demo.notification.dto.NotificationMessage;
import com.example.demo.notification.dto.NotificationResponse;
import com.example.demo.notification.dto.RecentNotificationResponse;
import com.example.demo.notification.dto.UpdateNotificationRequest;
import com.example.demo.notification.exception.NotificationNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationCacheService notificationCacheService;
    private final NotificationMessagePublisher notificationMessagePublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationCacheService notificationCacheService,
            NotificationMessagePublisher notificationMessagePublisher
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationCacheService = notificationCacheService;
        this.notificationMessagePublisher = notificationMessagePublisher;
    }

    @Transactional
    public NotificationResponse create(CreateNotificationRequest request) {
        Notification notification = new Notification(
                request.type(),
                request.recipient(),
                request.subject(),
                request.content()
        );
        Notification saved = notificationRepository.saveAndFlush(notification);
        NotificationResponse response = toResponse(saved);

        notificationMessagePublisher.publish(NotificationMessage.from(response));
        notificationCacheService.put(response);
        notificationCacheService.addRecent(response);

        return response;
    }

    @Transactional(readOnly = true)
    public NotificationResponse getById(Long id) {
        return notificationCacheService.findById(id)
                .orElseGet(() -> {
                    Notification notification = notificationRepository.findById(id)
                            .orElseThrow(() -> new NotificationNotFoundException(id));
                    NotificationResponse response = toResponse(notification);
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
                .map(NotificationService::toResponse)
                .toList();
        notificationCacheService.replaceRecent(responses);
        return responses.stream()
                .map(NotificationService::toRecentResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse update(Long id, UpdateNotificationRequest request) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        notification.update(request.subject(), request.content());
        NotificationResponse response = toResponse(notification);

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

    static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getRecipient(),
                notification.getSubject(),
                notification.getContent(),
                notification.getCreatedAt(),
                notification.getUpdatedAt()
        );
    }

    static RecentNotificationResponse toRecentResponse(NotificationResponse response) {
        return new RecentNotificationResponse(
                response.id(),
                response.type(),
                response.recipient(),
                response.subject(),
                response.createdAt()
        );
    }
}
