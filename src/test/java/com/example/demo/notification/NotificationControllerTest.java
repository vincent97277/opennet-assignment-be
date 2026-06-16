package com.example.demo.notification;

import com.example.demo.notification.dto.CreateNotificationRequest;
import com.example.demo.notification.dto.NotificationResponse;
import com.example.demo.notification.exception.NotificationDeliveryException;
import com.example.demo.notification.exception.NotificationNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.example.demo.notification.NotificationFixtures.CONTENT;
import static com.example.demo.notification.NotificationFixtures.DELIVERY_FAILURE_MESSAGE;
import static com.example.demo.notification.NotificationFixtures.EMAIL_RECIPIENT;
import static com.example.demo.notification.NotificationFixtures.EMAIL_TYPE;
import static com.example.demo.notification.NotificationFixtures.MISSING_NOTIFICATION_ID;
import static com.example.demo.notification.NotificationFixtures.NOTIFICATION_ID;
import static com.example.demo.notification.NotificationFixtures.SUBJECT;
import static com.example.demo.notification.NotificationFixtures.UPDATED_CONTENT;
import static com.example.demo.notification.NotificationFixtures.UPDATED_SUBJECT;
import static com.example.demo.notification.NotificationFixtures.createEmailRequest;
import static com.example.demo.notification.NotificationFixtures.createSmsRequest;
import static com.example.demo.notification.NotificationFixtures.recentResponse;
import static com.example.demo.notification.NotificationFixtures.response;
import static com.example.demo.notification.NotificationFixtures.updateRequest;
import static org.assertj.core.api.Assertions.assertThat;
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

    private static final String NOTIFICATIONS_PATH = "/notifications";
    private static final String NOTIFICATION_BY_ID_PATH = "/notifications/{id}";
    private static final String RECENT_NOTIFICATIONS_PATH = "/notifications/recent";
    private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
    private static final String DELIVERY_ERROR_CODE = "NOTIFICATION_DELIVERY_FAILED";
    private static final String NOT_FOUND_ERROR_CODE = "NOTIFICATION_NOT_FOUND";
    private static final String NOT_BLANK_MESSAGE = "must not be blank";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void createNotificationReturnsCreatedResponse() throws Exception {
        NotificationResponse response = response(NOTIFICATION_ID, EMAIL_TYPE, SUBJECT, CONTENT);
        when(notificationService.create(any(CreateNotificationRequest.class))).thenReturn(response);

        mockMvc.perform(post(NOTIFICATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createEmailRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$.type").value("email"))
                .andExpect(jsonPath("$.recipient").value(EMAIL_RECIPIENT))
                .andExpect(jsonPath("$.subject").value(SUBJECT))
                .andExpect(jsonPath("$.content").value(CONTENT));

        ArgumentCaptor<CreateNotificationRequest> requestCaptor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isEqualTo(createEmailRequest());
    }

    @Test
    void createNotificationRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post(NOTIFICATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "%s",
                                  "recipient": "",
                                  "subject": "%s",
                                  "content": ""
                                }
                                """.formatted(EMAIL_TYPE.toJson(), SUBJECT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VALIDATION_ERROR_CODE))
                .andExpect(jsonPath("$.fields.recipient").value(NOT_BLANK_MESSAGE))
                .andExpect(jsonPath("$.fields.content").value(NOT_BLANK_MESSAGE));
    }

    @Test
    void createNotificationReturnsServiceUnavailableWhenMessagePublishFails() throws Exception {
        when(notificationService.create(any(CreateNotificationRequest.class)))
                .thenThrow(new NotificationDeliveryException(DELIVERY_FAILURE_MESSAGE));

        mockMvc.perform(post(NOTIFICATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createSmsRequest())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(DELIVERY_ERROR_CODE));
    }

    @Test
    void getNotificationReturnsNotificationById() throws Exception {
        when(notificationService.getById(NOTIFICATION_ID)).thenReturn(response(NOTIFICATION_ID, EMAIL_TYPE, SUBJECT, CONTENT));

        mockMvc.perform(get(NOTIFICATION_BY_ID_PATH, NOTIFICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$.type").value("email"));
    }

    @Test
    void getNotificationReturnsNotFoundWhenMissing() throws Exception {
        when(notificationService.getById(MISSING_NOTIFICATION_ID))
                .thenThrow(new NotificationNotFoundException(MISSING_NOTIFICATION_ID));

        mockMvc.perform(get(NOTIFICATION_BY_ID_PATH, MISSING_NOTIFICATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(NOT_FOUND_ERROR_CODE));
    }

    @Test
    void getRecentNotificationsReturnsRecentList() throws Exception {
        when(notificationService.getRecent()).thenReturn(List.of(recentResponse(NOTIFICATION_ID)));

        mockMvc.perform(get(RECENT_NOTIFICATIONS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$[0].recipient").value(EMAIL_RECIPIENT))
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }

    @Test
    void updateNotificationReturnsUpdatedNotification() throws Exception {
        NotificationResponse response = response(NOTIFICATION_ID, EMAIL_TYPE, UPDATED_SUBJECT, UPDATED_CONTENT);
        when(notificationService.update(NOTIFICATION_ID, updateRequest()))
                .thenReturn(response);

        mockMvc.perform(put(NOTIFICATION_BY_ID_PATH, NOTIFICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(UPDATED_SUBJECT))
                .andExpect(jsonPath("$.content").value(UPDATED_CONTENT));
    }

    @Test
    void deleteNotificationReturnsNoContent() throws Exception {
        mockMvc.perform(delete(NOTIFICATION_BY_ID_PATH, NOTIFICATION_ID))
                .andExpect(status().isNoContent());

        verify(notificationService).delete(NOTIFICATION_ID);
    }
}
