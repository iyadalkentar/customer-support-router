package com.ikdev.customersupportrouter.chatservice.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;
import com.ikdev.customersupportrouter.chatservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock
    private ClassificationService classificationService;

    @Mock
    private RoutingPolicy routingPolicy;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private EscalationService escalationService;

    @Mock
    private RoutingMetrics routingMetrics;

    @InjectMocks
    private RoutingService routingService;

    private final ClassificationResult result = new ClassificationResult(
            42L, 7L, "COMPLAINT", "NEGATIVE", "HIGH", UUID.randomUUID(), 1);

    private Message classifiedMessage(RoutingDecision storedDecision) {
        Message message = new Message();
        message.setId(42L);
        message.setIntent("COMPLAINT");
        message.setSentiment("NEGATIVE");
        message.setUrgency("HIGH");
        message.setRoutingDecision(storedDecision);
        return message;
    }

    @Test
    void applyClassificationAndRoute_unknownMessageId_dropsWithoutRouting() {
        when(classificationService.applyClassification(result)).thenReturn(Optional.empty());

        routingService.applyClassificationAndRoute(result);

        verifyNoInteractions(routingPolicy);
        verifyNoInteractions(escalationService);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void applyClassificationAndRoute_autoRespond_recordsDecisionNoEscalation() {
        Message message = classifiedMessage(null);
        when(classificationService.applyClassification(result)).thenReturn(Optional.of(message));
        when(routingPolicy.decide("COMPLAINT", "NEGATIVE", "HIGH")).thenReturn(RoutingDecision.AUTO_RESPOND);

        routingService.applyClassificationAndRoute(result);

        assertThat(message.getRoutingDecision()).isEqualTo(RoutingDecision.AUTO_RESPOND);
        verify(messageRepository).save(message);
        verifyNoInteractions(escalationService);
    }

    @Test
    void applyClassificationAndRoute_escalation_newDecision_triggersEscalation() {
        Message message = classifiedMessage(null);
        when(classificationService.applyClassification(result)).thenReturn(Optional.of(message));
        when(routingPolicy.decide("COMPLAINT", "NEGATIVE", "HIGH")).thenReturn(RoutingDecision.ESCALATE_TO_HUMAN);

        routingService.applyClassificationAndRoute(result);

        assertThat(message.getRoutingDecision()).isEqualTo(RoutingDecision.ESCALATE_TO_HUMAN);
        verify(escalationService).escalate(message, RoutingDecision.ESCALATE_TO_HUMAN);
        verify(messageRepository).save(message);
    }

    @Test
    void applyClassificationAndRoute_redelivery_sameDecision_skipsEscalation() {
        Message message = classifiedMessage(RoutingDecision.ESCALATE_TO_HUMAN);
        when(classificationService.applyClassification(result)).thenReturn(Optional.of(message));
        when(routingPolicy.decide("COMPLAINT", "NEGATIVE", "HIGH")).thenReturn(RoutingDecision.ESCALATE_TO_HUMAN);

        routingService.applyClassificationAndRoute(result);

        verifyNoInteractions(escalationService);
        verify(messageRepository).save(message);
    }

    @Test
    void applyClassificationAndRoute_decisionChange_autoRespondToEscalate_triggersEscalation() {
        Message message = classifiedMessage(RoutingDecision.AUTO_RESPOND);
        when(classificationService.applyClassification(result)).thenReturn(Optional.of(message));
        when(routingPolicy.decide("COMPLAINT", "NEGATIVE", "HIGH")).thenReturn(RoutingDecision.ESCALATE_TO_HUMAN);

        routingService.applyClassificationAndRoute(result);

        verify(escalationService).escalate(message, RoutingDecision.ESCALATE_TO_HUMAN);
        assertThat(message.getRoutingDecision()).isEqualTo(RoutingDecision.ESCALATE_TO_HUMAN);
    }

    @Test
    void applyClassificationAndRoute_deescalation_escalationToAutoRespond_deescalates() {
        Message message = classifiedMessage(RoutingDecision.ESCALATE_TO_HUMAN);
        when(classificationService.applyClassification(result)).thenReturn(Optional.of(message));
        when(routingPolicy.decide("COMPLAINT", "NEGATIVE", "HIGH")).thenReturn(RoutingDecision.AUTO_RESPOND);

        routingService.applyClassificationAndRoute(result);

        assertThat(message.getRoutingDecision()).isEqualTo(RoutingDecision.AUTO_RESPOND);
        verify(escalationService).deescalate(message);
        verify(escalationService, never()).escalate(any(), any());
        verify(messageRepository).save(message);
    }
}
