package com.ikdev.customersupportrouter.chatservice.repository;

import java.util.List;

import com.ikdev.customersupportrouter.chatservice.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** Newest-first by creation order, capped by {@code pageable}. */
    List<Conversation> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
