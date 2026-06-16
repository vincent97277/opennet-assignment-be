package com.example.demo.notification.service;

import com.example.demo.notification.domain.NotificationType;
import com.example.demo.notification.dto.NotificationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    private NotificationCacheService notificationCacheService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        notificationCacheService = new NotificationCacheService(redisTemplate, objectMapper);
    }

    @Test
    void putStoresNotificationJsonWithTenMinuteTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        notificationCacheService.put(response(123L));

        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.eq("notification:123"),
                org.mockito.ArgumentMatchers.contains("\"id\":123"),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10))
        );
    }

    @Test
    void cacheCreatedStoresNotificationAndMovesIdToFront() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        notificationCacheService.cacheCreated(response(123L));

        verify(listOperations).remove("notifications:recent", 0, "123");
        verify(listOperations).leftPush("notifications:recent", "123");
        verify(listOperations).trim("notifications:recent", 0, 9);
    }

    @Test
    void findRecentFallsBackWhenAnyCachedEntryIsMissing() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(listOperations.range("notifications:recent", 0, 9)).thenReturn(List.of("123", "456"));
        when(valueOperations.multiGet(List.of("notification:123", "notification:456")))
                .thenReturn(Arrays.asList("{\"id\":123}", null));

        assertThat(notificationCacheService.findRecent()).isEmpty();
    }

    private NotificationResponse response(Long id) {
        return new NotificationResponse(
                id,
                NotificationType.EMAIL,
                "user@example.com",
                "Welcome!",
                "Thanks",
                Instant.parse("2025-07-15T12:01:02Z"),
                Instant.parse("2025-07-15T12:01:02Z")
        );
    }
}
