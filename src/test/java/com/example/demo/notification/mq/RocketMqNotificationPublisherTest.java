package com.example.demo.notification.mq;

import com.example.demo.notification.dto.NotificationMessage;
import com.example.demo.notification.exception.NotificationDeliveryException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import static com.example.demo.notification.NotificationFixtures.CONTENT;
import static com.example.demo.notification.NotificationFixtures.CREATED_AT;
import static com.example.demo.notification.NotificationFixtures.EMAIL_RECIPIENT;
import static com.example.demo.notification.NotificationFixtures.EMAIL_TYPE;
import static com.example.demo.notification.NotificationFixtures.NOTIFICATION_ID;
import static com.example.demo.notification.NotificationFixtures.SUBJECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RocketMqNotificationPublisherTest {

    private static final String TOPIC = "notification-topic";

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    private RocketMqNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RocketMqNotificationPublisher(rocketMQTemplate, TOPIC);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishSendsMessageWithTopicTagKeyAndPayload() {
        NotificationMessage message = notificationMessage();

        publisher.publish(message);

        ArgumentCaptor<Message<NotificationMessage>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(eq(TOPIC + ":" + EMAIL_TYPE.name()), messageCaptor.capture());

        Message<NotificationMessage> rocketMessage = messageCaptor.getValue();
        assertThat(rocketMessage.getPayload()).isEqualTo(message);
        assertThat(rocketMessage.getHeaders().get(RocketMQHeaders.KEYS)).isEqualTo(NOTIFICATION_ID.toString());
        assertThat(rocketMessage.getHeaders().get(RocketMQHeaders.TAGS)).isEqualTo(EMAIL_TYPE.name());
    }

    @Test
    void publishWrapsRocketMqFailure() {
        NotificationMessage message = notificationMessage();
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rocketMQTemplate)
                .syncSend(eq(TOPIC + ":" + EMAIL_TYPE.name()), any(Message.class));

        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessage("Unable to publish notification message")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private static NotificationMessage notificationMessage() {
        return new NotificationMessage(
                NOTIFICATION_ID,
                EMAIL_TYPE,
                EMAIL_RECIPIENT,
                SUBJECT,
                CONTENT,
                CREATED_AT
        );
    }
}
