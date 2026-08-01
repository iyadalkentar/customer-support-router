package com.ikdev.customersupportrouter.chatservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;


/**
 * JPA entity representing the {@code messages} table.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"conversation"})
public class Message {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(nullable = false, length = 50)
    private String sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(length = 50)
    private String intent;

    @Column(length = 50)
    private String sentiment;

    @Column(length = 50)
    private String urgency;

    @Column(name = "trace_id")
    private UUID traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_decision", length = 50)
    private RoutingDecision routingDecision;
}
