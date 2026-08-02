package com.ikdev.customersupportrouter.aiclassifierservice.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConversationContextFormatterTest {

    private final ConversationContextFormatter formatter = new ConversationContextFormatter();

    private ConversationContextMessage message(String sender, String content) {
        return new ConversationContextMessage(1L, 7L, sender, content,
                OffsetDateTime.parse("2026-08-02T10:00:00Z"));
    }

    @Test
    void emptyOrNull_returnsPlaceholderSentence() {
        assertThat(formatter.format(List.of())).isEqualTo("No prior conversation messages.");
        assertThat(formatter.format(null)).isEqualTo("No prior conversation messages.");
    }

    @Test
    void singleMessage_rendersNumberedLine() {
        assertThat(formatter.format(List.of(message("customer", "Hello"))))
                .isEqualTo("1. [customer] Hello");
    }

    @Test
    void multipleMessages_rendersOldestFirst() {
        String rendered = formatter.format(List.of(
                message("customer", "I cannot log in"),
                message("customer", "Now it says account locked")));
        assertThat(rendered).isEqualTo(
                "1. [customer] I cannot log in\n2. [customer] Now it says account locked");
    }
}
