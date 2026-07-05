package com.ikdev.customersupportrouter.chatservice.repository;

import com.ikdev.customersupportrouter.chatservice.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    // Additional query methods can be defined here
}
