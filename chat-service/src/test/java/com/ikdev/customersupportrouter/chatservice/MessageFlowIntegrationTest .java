package com.ikdev.customersupportrouter.chatservice;

import com.ikdev.customersupportrouter.chatservice.dto.CreateMessageRequest;
import com.ikdev.customersupportrouter.chatservice.dto.MessageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer; // new package
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.web.servlet.client.RestTestClient;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class MessageFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void postingMessageWithoutConversationId_createsNewConversationAndPersistsMessage() {
        CreateMessageRequest request = new CreateMessageRequest(null, "customer", "Hello, I need help");

        restTestClient.post().uri("/messages")
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody(MessageResponse.class)
                .value(body -> {
                    assert body.id() != null;
                    assert body.createdAt() != null;
                    assert body.conversationId() != null;
                });
    }

    @Test
    void gettingMessagesForUnknownConversation_returns404() {
        restTestClient.get().uri("/conversations/999999/messages")
                .exchange()
                .expectStatus().isEqualTo(404);
    }
}