package com.example.demo.notification.mq;

import com.example.demo.notification.NotificationPublisher;
import com.example.demo.notification.dto.NotificationMessage;
import com.example.demo.notification.exception.NotificationDeliveryException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class RocketMqNotificationPublisher implements NotificationPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;

    public RocketMqNotificationPublisher(
            RocketMQTemplate rocketMQTemplate,
            @Value("${notification.rocketmq.topic:notification-topic}") String topic
    ) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(NotificationMessage message) {
        try {
            Message<NotificationMessage> rocketMessage = MessageBuilder.withPayload(message)
                    .setHeader(RocketMQHeaders.KEYS, message.id().toString())
                    .setHeader(RocketMQHeaders.TAGS, message.type().name())
                    .build();
            rocketMQTemplate.syncSend(topic + ":" + message.type().name(), rocketMessage);
        } catch (Exception ex) {
            throw new NotificationDeliveryException("Unable to publish notification message", ex);
        }
    }
}
