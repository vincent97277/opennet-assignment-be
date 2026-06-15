package com.example.demo.notification;

import com.example.demo.notification.dto.CreateNotificationRequest;
import com.example.demo.notification.dto.NotificationResponse;
import com.example.demo.notification.dto.RecentNotificationResponse;
import com.example.demo.notification.dto.UpdateNotificationRequest;
import com.example.demo.notification.exception.NotificationDeliveryException;
import com.example.demo.notification.exception.NotificationNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void createNotificationReturnsCreatedResponse() throws Exception {
        NotificationResponse response = response(123L, NotificationType.EMAIL, "Welcome!", "Thanks");
        when(notificationService.create(any(CreateNotificationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateNotificationRequest(
                                NotificationType.EMAIL,
                                "user@example.com",
                                "Welcome!",
                                "Thanks"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.type").value("email"))
                .andExpect(jsonPath("$.recipient").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("Welcome!"))
                .andExpect(jsonPath("$.content").value("Thanks"));
    }

    @Test
    void createNotificationRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "email",
                                  "recipient": "",
                                  "subject": "Welcome!",
                                  "content": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createNotificationReturnsServiceUnavailableWhenMessagePublishFails() throws Exception {
        when(notificationService.create(any(CreateNotificationRequest.class)))
                .thenThrow(new NotificationDeliveryException("Unable to publish notification message"));

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateNotificationRequest(
                                NotificationType.SMS,
                                "+15551234567",
                                "Welcome!",
                                "Thanks"
                        ))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_DELIVERY_FAILED"));
    }

    @Test
    void getNotificationReturnsNotificationById() throws Exception {
        when(notificationService.getById(123L)).thenReturn(response(123L, NotificationType.EMAIL, "Welcome!", "Thanks"));

        mockMvc.perform(get("/notifications/{id}", 123L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.type").value("email"));
    }

    @Test
    void getNotificationReturnsNotFoundWhenMissing() throws Exception {
        when(notificationService.getById(404L)).thenThrow(new NotificationNotFoundException(404L));

        mockMvc.perform(get("/notifications/{id}", 404L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void getRecentNotificationsReturnsRecentList() throws Exception {
        when(notificationService.getRecent()).thenReturn(List.of(new RecentNotificationResponse(
                123L,
                NotificationType.EMAIL,
                "user@example.com",
                "Welcome!",
                Instant.parse("2025-07-15T12:01:02Z")
        )));

        mockMvc.perform(get("/notifications/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(123))
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }

    @Test
    void updateNotificationReturnsUpdatedNotification() throws Exception {
        NotificationResponse response = response(123L, NotificationType.EMAIL, "Updated subject", "Updated content");
        when(notificationService.update(123L, new UpdateNotificationRequest("Updated subject", "Updated content")))
                .thenReturn(response);

        mockMvc.perform(put("/notifications/{id}", 123L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateNotificationRequest(
                                "Updated subject",
                                "Updated content"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Updated subject"))
                .andExpect(jsonPath("$.content").value("Updated content"));
    }

    @Test
    void deleteNotificationReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/notifications/{id}", 123L))
                .andExpect(status().isNoContent());

        verify(notificationService).delete(123L);
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
