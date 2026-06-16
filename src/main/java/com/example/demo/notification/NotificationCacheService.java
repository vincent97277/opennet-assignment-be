package com.example.demo.notification;

import com.example.demo.notification.dto.NotificationResponse;
import com.example.demo.notification.dto.RecentNotificationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCacheService {

    private static final Duration NOTIFICATION_TTL = Duration.ofMinutes(10);
    private static final String NOTIFICATION_KEY_PREFIX = "notification:";
    private static final String RECENT_KEY = "notifications:recent";
    private static final int RECENT_LIMIT = 10;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<NotificationResponse> findById(Long id) {
        try {
            String json = redisTemplate.opsForValue().get(notificationKey(id));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, NotificationResponse.class));
        } catch (Exception ex) {
            log.warn("Unable to read notification {} from Redis cache", id, ex);
            return Optional.empty();
        }
    }

    public List<RecentNotificationResponse> findRecent() {
        try {
            List<String> ids = redisTemplate.opsForList().range(RECENT_KEY, 0, RECENT_LIMIT - 1);
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }

            List<String> keys = ids.stream()
                    .map(id -> notificationKey(Long.valueOf(id)))
                    .toList();
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null || values.size() != ids.size() || values.stream().anyMatch(value -> value == null)) {
                return List.of();
            }

            List<RecentNotificationResponse> recent = new ArrayList<>();
            for (String value : values) {
                NotificationResponse response = objectMapper.readValue(value, NotificationResponse.class);
                recent.add(RecentNotificationResponse.from(response));
            }
            return recent;
        } catch (Exception ex) {
            log.warn("Unable to read recent notifications from Redis cache", ex);
            return List.of();
        }
    }

    public void put(NotificationResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    notificationKey(response.id()),
                    objectMapper.writeValueAsString(response),
                    NOTIFICATION_TTL
            );
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize notification {} for Redis cache", response.id(), ex);
        } catch (Exception ex) {
            log.warn("Unable to write notification {} to Redis cache", response.id(), ex);
        }
    }

    public void cacheCreated(NotificationResponse response) {
        try {
            put(response);
            String id = response.id().toString();
            redisTemplate.opsForList().remove(RECENT_KEY, 0, id);
            redisTemplate.opsForList().leftPush(RECENT_KEY, id);
            redisTemplate.opsForList().trim(RECENT_KEY, 0, RECENT_LIMIT - 1);
        } catch (Exception ex) {
            log.warn("Unable to cache created notification {}", response.id(), ex);
        }
    }

    public void replaceRecent(List<NotificationResponse> responses) {
        try {
            redisTemplate.delete(RECENT_KEY);
            if (responses.isEmpty()) {
                return;
            }
            for (NotificationResponse response : responses) {
                put(response);
                redisTemplate.opsForList().rightPush(RECENT_KEY, response.id().toString());
            }
            redisTemplate.opsForList().trim(RECENT_KEY, 0, RECENT_LIMIT - 1);
        } catch (Exception ex) {
            log.warn("Unable to rebuild recent notification cache", ex);
        }
    }

    public void evict(Long id) {
        try {
            redisTemplate.delete(notificationKey(id));
            redisTemplate.opsForList().remove(RECENT_KEY, 0, id.toString());
        } catch (Exception ex) {
            log.warn("Unable to evict notification {} from Redis cache", id, ex);
        }
    }

    public void evictRecent() {
        try {
            redisTemplate.delete(RECENT_KEY);
        } catch (Exception ex) {
            log.warn("Unable to evict recent notification cache", ex);
        }
    }

    private String notificationKey(Long id) {
        return NOTIFICATION_KEY_PREFIX + id;
    }
}
