package com.ikdev.customersupportrouter.chatservice.kafka;

import com.ikdev.customersupportrouter.chatservice.event.MessageEvent;
import com.ikdev.customersupportrouter.chatservice.event.MessagePersistedEvent;
import com.ikdev.customersupportrouter.chatservice.service.MessageMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class MessageEventPublisherTest {

        private static final String TOPIC = "incoming-messages";

        @Mock
        private KafkaTemplate<String, MessageEvent> kafkaTemplate;

        @Mock
        private MessageMetrics messageMetrics;

        @InjectMocks
        private MessageEventPublisher messageEventPublisher;

        @Test
        void onMessagePersisted_sendsEventToIncomingMessagesTopic_keyedByConversationId() {
                MessageEvent event = new MessageEvent(
                                42L,
                                7L,
                                "customer",
                                "hello there",
                                OffsetDateTime.parse("2026-07-07T12:00:00Z"),
                                1,
                                UUID.randomUUID());
                MessagePersistedEvent persistedEvent = new MessagePersistedEvent(event);

                messageEventPublisher.onMessagePersisted(persistedEvent);

                ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<MessageEvent> payloadCaptor = ArgumentCaptor.forClass(MessageEvent.class);

                verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());
                verifyNoMoreInteractions(kafkaTemplate);

                assertThat(topicCaptor.getValue()).isEqualTo(TOPIC);
                assertThat(keyCaptor.getValue()).isEqualTo("7"); // String.valueOf(conversationId)
                assertThat(payloadCaptor.getValue()).isEqualTo(event);
        }

        @Test
        void onMessagePersisted_keyIsStringifiedConversationId_notMessageId() {
                // Regression guard: ordering is per-conversation, not per-message, so the
                // partition key must be conversationId even though messageId differs.
                MessageEvent event = new MessageEvent(
                                999L,
                                123L,
                                "agent",
                                "following up",
                                OffsetDateTime.now(),
                                1,
                                UUID.randomUUID());

                messageEventPublisher.onMessagePersisted(new MessagePersistedEvent(event));

                ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
                verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq(TOPIC), keyCaptor.capture(),
                                org.mockito.ArgumentMatchers.eq(event));

                assertThat(keyCaptor.getValue())
                                .isEqualTo("123")
                                .isNotEqualTo("999");
        }
}