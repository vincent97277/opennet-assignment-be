package com.example.demo.notification;

import com.example.demo.notification.dto.NotificationResponse;
import com.example.demo.notification.dto.RecentNotificationResponse;
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
import java.util.Arrays;
import java.util.List;

import static com.example.demo.notification.NotificationFixtures.NOTIFICATION_ID;
import static com.example.demo.notification.NotificationFixtures.SECOND_NOTIFICATION_ID;
import static com.example.demo.notification.NotificationFixtures.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCacheServiceTest {

    private static final Duration NOTIFICATION_TTL = Duration.ofMinutes(10);
    private static final String NOTIFICATION_KEY_PREFIX = "notification:";
    private static final String RECENT_KEY = "notifications:recent";
    private static final long REMOVE_ALL_MATCHES = 0;
    private static final int RECENT_START_INDEX = 0;
    private static final int RECENT_END_INDEX = 9;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    private ObjectMapper objectMapper;
    private NotificationCacheService notificationCacheService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        notificationCacheService = new NotificationCacheService(redisTemplate, objectMapper);
    }

    @Test
    void putStoresNotificationJsonWithTenMinuteTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        notificationCacheService.put(response(NOTIFICATION_ID));

        verify(valueOperations).set(
                eq(notificationKey(NOTIFICATION_ID)),
                contains("\"id\":" + NOTIFICATION_ID),
                eq(NOTIFICATION_TTL)
        );
    }

    @Test
    void cacheCreatedStoresNotificationAndMovesIdToFront() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        notificationCacheService.cacheCreated(response(NOTIFICATION_ID));

        String notificationId = NOTIFICATION_ID.toString();
        verify(valueOperations).set(
                eq(notificationKey(NOTIFICATION_ID)),
                contains("\"id\":" + NOTIFICATION_ID),
                eq(NOTIFICATION_TTL)
        );
        verify(listOperations).remove(RECENT_KEY, REMOVE_ALL_MATCHES, notificationId);
        verify(listOperations).leftPush(RECENT_KEY, notificationId);
        verify(listOperations).trim(RECENT_KEY, RECENT_START_INDEX, RECENT_END_INDEX);
    }

    @Test
    void findRecentReturnsCachedNotificationsInStoredOrder() throws Exception {
        NotificationResponse first = response(NOTIFICATION_ID);
        NotificationResponse second = response(SECOND_NOTIFICATION_ID);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(listOperations.range(RECENT_KEY, RECENT_START_INDEX, RECENT_END_INDEX))
                .thenReturn(List.of(NOTIFICATION_ID.toString(), SECOND_NOTIFICATION_ID.toString()));
        when(valueOperations.multiGet(List.of(notificationKey(NOTIFICATION_ID), notificationKey(SECOND_NOTIFICATION_ID))))
                .thenReturn(List.of(objectMapper.writeValueAsString(first), objectMapper.writeValueAsString(second)));

        assertThat(notificationCacheService.findRecent())
                .containsExactly(RecentNotificationResponse.from(first), RecentNotificationResponse.from(second));
    }

    @Test
    void findRecentFallsBackWhenAnyCachedEntryIsMissing() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(listOperations.range(RECENT_KEY, RECENT_START_INDEX, RECENT_END_INDEX))
                .thenReturn(List.of(NOTIFICATION_ID.toString(), SECOND_NOTIFICATION_ID.toString()));
        when(valueOperations.multiGet(List.of(notificationKey(NOTIFICATION_ID), notificationKey(SECOND_NOTIFICATION_ID))))
                .thenReturn(Arrays.asList("{\"id\":" + NOTIFICATION_ID + "}", null));

        assertThat(notificationCacheService.findRecent()).isEmpty();
    }

    private String notificationKey(Long id) {
        return NOTIFICATION_KEY_PREFIX + id;
    }
}
