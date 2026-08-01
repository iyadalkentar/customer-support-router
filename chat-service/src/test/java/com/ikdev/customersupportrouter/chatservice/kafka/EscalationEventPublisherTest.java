package com.ikdev.customersupportrouter.chatservice.kafka;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;
import com.ikdev.customersupportrouter.chatservice.event.EscalationEvent;
import com.ikdev.customersupportrouter.chatservice.event.EscalationPersistedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EscalationEventPublisherTest {

    private static final String TOPIC = "escalations";

    @Mock
    private KafkaTemplate<String, EscalationEvent> kafkaTemplate;

    @InjectMocks
    private EscalationEventPublisher escalationEventPublisher;

    @Test
    void onEscalationPersisted_sendsEventToEscalationsTopic_keyedByConversationId() {
        EscalationEvent event = new EscalationEvent(
                500L, 7L, 42L, UUID.randomUUID(),
                "COMPLAINT", "NEGATIVE", "HIGH", RoutingDecision.ESCALATE_TO_HUMAN, 1);
        EscalationPersistedEvent persistedEvent = new EscalationPersistedEvent(event);

        escalationEventPublisher.onEscalationPersisted(persistedEvent);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EscalationEvent> payloadCaptor = ArgumentCaptor.forClass(EscalationEvent.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());
        verifyNoMoreInteractions(kafkaTemplate);

        assertThat(topicCaptor.getValue()).isEqualTo(TOPIC);
        assertThat(keyCaptor.getValue()).isEqualTo("7"); // String.valueOf(conversationId)
        assertThat(payloadCaptor.getValue()).isEqualTo(event);
    }

    @Test
    void onEscalationPersisted_keyIsStringifiedConversationId_notTicketId() {
        // Regression guard: per-conversation ordering must key on conversationId,
        // not the (potentially reused) ticket id.
        EscalationEvent event = new EscalationEvent(
                900L, 123L, 99L, UUID.randomUUID(),
                "BUG_REPORT", "NEUTRAL", "LOW", RoutingDecision.CREATE_TICKET, 1);

        escalationEventPublisher.onEscalationPersisted(new EscalationPersistedEvent(event));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq(TOPIC), keyCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(event));

        assertThat(keyCaptor.getValue())
                .isEqualTo("123")
                .isNotEqualTo("900");
    }
}
