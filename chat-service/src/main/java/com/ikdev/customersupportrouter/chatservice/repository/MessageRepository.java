package com.ikdev.customersupportrouter.chatservice.repository;

import java.util.Collection;
import java.util.List;

import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    /**
     * Oldest-first by commit order, with {@code id} as a deterministic tiebreak.
     * {@code createdAt} is Postgres-microsecond precision while the Java value is
     * nanosecond, so near-simultaneous inserts can tie on timestamp alone; without
     * the secondary sort the order (and therefore the Redis backfill window) would
     * be nondeterministic. Ids are sequential, so {@code id ASC} equals commit order.
     */
    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    /**
     * Whether any message in the conversation (other than the given one) still
     * carries an escalation {@link RoutingDecision}. Used by
     * {@link com.ikdev.customersupportrouter.chatservice.service.EscalationService#deescalate}
     * to decide whether the conversation's OPEN ticket can be closed.
     */
    boolean existsByConversationIdAndRoutingDecisionInAndIdNot(
            Long conversationId, Collection<RoutingDecision> routingDecisions, Long messageId);
}
