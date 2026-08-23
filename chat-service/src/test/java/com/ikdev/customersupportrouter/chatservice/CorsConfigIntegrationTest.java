package com.ikdev.customersupportrouter.chatservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CORS configuration.
 *
 * <p>Verifies that:
 * 1. Requests from allowed origins receive permissive CORS headers.
 * 2. Requests from unlisted origins do NOT receive CORS headers permitting them.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class CorsConfigIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void corsRequest_fromAllowedOrigin_receivesAllowOriginHeader() {
        String allowedOrigin = "http://localhost:5173";

        restTestClient.get().uri("/conversations")
                .header("Origin", allowedOrigin)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches("Access-Control-Allow-Origin", allowedOrigin);
    }

    @Test
    void corsRequest_fromUnlistedOrigin_doesNotReceiveAllowOriginHeader() {
        // Spring's CORS interceptor rejects a disallowed Origin with 403 before the
        // request reaches the controller, rather than serving 200 without the header.
        String unlistedOrigin = "http://evil.example.com";

        restTestClient.get().uri("/conversations")
                .header("Origin", unlistedOrigin)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }
}
