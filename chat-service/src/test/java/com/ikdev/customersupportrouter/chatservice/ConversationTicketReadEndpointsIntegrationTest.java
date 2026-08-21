package com.ikdev.customersupportrouter.chatservice;

import com.ikdev.customersupportrouter.chatservice.dto.ConversationResponse;
import com.ikdev.customersupportrouter.chatservice.dto.TicketResponse;
import com.ikdev.customersupportrouter.chatservice.entity.Conversation;
import com.ikdev.customersupportrouter.chatservice.entity.Ticket;
import com.ikdev.customersupportrouter.chatservice.entity.TicketStatus;
import com.ikdev.customersupportrouter.chatservice.repository.ConversationRepository;
import com.ikdev.customersupportrouter.chatservice.repository.TicketRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConversationTicketReadEndpointsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private TicketRepository ticketRepository;

    // --- GET /conversations ---

    // NOTE ON TEST ORDERING: this class shares one Testcontainers Postgres
    // instance across all @Test methods in the class (no per-test rollback),
    // so a scenario that needs the *whole table* to be empty only holds before
    // any other method in this class has inserted a row. @TestMethodOrder +
    // @Order(1) pins that one scenario first; every other test below
    // deliberately asserts against the specific ids it created (contains /
    // relative-order checks) rather than exact table-wide counts, so they stay
    // correct regardless of what earlier tests in the class left behind.

    @Test
    @Order(1)
    void getAllConversations_withNoConversations_returns200WithEmptyArray() {
        restTestClient.get().uri("/conversations")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<ConversationResponse>>() {})
                .value(conversations -> {
                    assertThat(conversations).isEmpty();
                });
    }

    @Test
    @Order(10)
    void getAllConversations_withMultipleConversations_returns200OrderedByCreatedAtDesc() {
        Conversation conv1 = new Conversation();
        conversationRepository.save(conv1);

        Conversation conv2 = new Conversation();
        conversationRepository.save(conv2);

        Conversation conv3 = new Conversation();
        conversationRepository.save(conv3);

        restTestClient.get().uri("/conversations")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<ConversationResponse>>() {})
                .value(conversations -> {
                    // Other tests in this class may have created their own conversations
                    // by this point — assert relative ordering among the ones this test
                    // created, not the table's total size.
                    List<Long> ids = conversations.stream().map(ConversationResponse::id).toList();
                    assertThat(ids).containsSubsequence(conv3.getId(), conv2.getId(), conv1.getId());
                });
    }

    // --- GET /conversations/{id} ---

    @Test
    @Order(20)
    void getConversation_withValidId_returns200WithConversationData() {
        Conversation conversation = new Conversation();
        Conversation saved = conversationRepository.save(conversation);

        restTestClient.get().uri("/conversations/{id}", saved.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ConversationResponse.class)
                .value(response -> {
                    assertThat(response.id()).isEqualTo(saved.getId());
                    assertThat(response.status()).isEqualTo("ACTIVE");
                    assertThat(response.createdAt()).isNotNull();
                    assertThat(response.updatedAt()).isNotNull();
                });
    }

    @Test
    @Order(21)
    void getConversation_withNonExistentId_returns404() {
        restTestClient.get().uri("/conversations/{id}", 999999L)
                .exchange()
                .expectStatus().isNotFound();
    }

    // --- GET /conversations/{id}/tickets ---

    @Test
    @Order(30)
    void getConversationTickets_withValidConversationHavingMultipleTickets_returns200OrderedByCreatedAtDesc() {
        Conversation conversation = new Conversation();
        Conversation savedConv = conversationRepository.save(conversation);

        // Create multiple tickets
        Ticket ticket1 = new Ticket();
        ticket1.setConversation(savedConv);
        ticket1.setStatus(TicketStatus.OPEN);
        ticketRepository.save(ticket1);

        Ticket ticket2 = new Ticket();
        ticket2.setConversation(savedConv);
        ticket2.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticket2);

        Ticket ticket3 = new Ticket();
        ticket3.setConversation(savedConv);
        ticket3.setStatus(TicketStatus.RESOLVED);
        ticketRepository.save(ticket3);

        restTestClient.get().uri("/conversations/{id}/tickets", savedConv.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TicketResponse>>() {})
                .value(tickets -> {
                    assertThat(tickets).hasSize(3);
                    // Expect newest first (descending order by createdAt)
                    assertThat(tickets.get(0).id()).isEqualTo(ticket3.getId());
                    assertThat(tickets.get(0).conversationId()).isEqualTo(savedConv.getId());
                    assertThat(tickets.get(0).status()).isEqualTo("RESOLVED");

                    assertThat(tickets.get(1).id()).isEqualTo(ticket2.getId());
                    assertThat(tickets.get(1).conversationId()).isEqualTo(savedConv.getId());
                    assertThat(tickets.get(1).status()).isEqualTo("IN_PROGRESS");

                    assertThat(tickets.get(2).id()).isEqualTo(ticket1.getId());
                    assertThat(tickets.get(2).conversationId()).isEqualTo(savedConv.getId());
                    assertThat(tickets.get(2).status()).isEqualTo("OPEN");
                });
    }

    @Test
    @Order(31)
    void getConversationTickets_withConversationHavingNoTickets_returns200WithEmptyArray() {
        Conversation conversation = new Conversation();
        Conversation savedConv = conversationRepository.save(conversation);

        restTestClient.get().uri("/conversations/{id}/tickets", savedConv.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TicketResponse>>() {})
                .value(tickets -> {
                    assertThat(tickets).isEmpty();
                });
    }

    @Test
    @Order(32)
    void getConversationTickets_withNonExistentConversationId_returns404() {
        restTestClient.get().uri("/conversations/{id}/tickets", 999999L)
                .exchange()
                .expectStatus().isNotFound();
    }

    // --- GET /tickets/{id} ---

    @Test
    @Order(40)
    void getTicket_withValidId_returns200WithTicketData() {
        Conversation conversation = new Conversation();
        Conversation savedConv = conversationRepository.save(conversation);

        Ticket ticket = new Ticket();
        ticket.setConversation(savedConv);
        ticket.setStatus(TicketStatus.OPEN);
        Ticket savedTicket = ticketRepository.save(ticket);

        restTestClient.get().uri("/tickets/{id}", savedTicket.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(TicketResponse.class)
                .value(response -> {
                    assertThat(response.id()).isEqualTo(savedTicket.getId());
                    assertThat(response.conversationId()).isEqualTo(savedConv.getId());
                    assertThat(response.status()).isEqualTo("OPEN");
                    assertThat(response.createdAt()).isNotNull();
                    assertThat(response.updatedAt()).isNotNull();
                });
    }

    @Test
    @Order(41)
    void getTicket_withNonExistentId_returns404() {
        restTestClient.get().uri("/tickets/{id}", 999999L)
                .exchange()
                .expectStatus().isNotFound();
    }

    // --- GET /tickets ---

    @Test
    @Order(50)
    void getAllTickets_withNoFilter_returns200WithAllTickets() {
        Conversation conversation = new Conversation();
        Conversation savedConv = conversationRepository.save(conversation);

        Ticket ticket1 = new Ticket();
        ticket1.setConversation(savedConv);
        ticket1.setStatus(TicketStatus.OPEN);
        ticketRepository.save(ticket1);

        Ticket ticket2 = new Ticket();
        ticket2.setConversation(savedConv);
        ticket2.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticket2);

        restTestClient.get().uri("/tickets")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TicketResponse>>() {})
                .value(tickets -> {
                    // Other tests in this class create their own tickets too, so
                    // assert these two are present rather than the table's exact size.
                    assertThat(tickets).extracting(TicketResponse::id)
                            .contains(ticket1.getId(), ticket2.getId());
                });
    }

    @Test
    @Order(51)
    void getAllTickets_withStatusFilterOpen_returns200WithOnlyOpenTickets() {
        // Two separate conversations: the schema enforces at most one OPEN
        // ticket per conversation (uq_tickets_one_open_per_conversation, see
        // phase-4-status-note.md), so two OPEN fixtures can't share one
        // conversation.
        Conversation conversationA = conversationRepository.save(new Conversation());
        Conversation conversationB = conversationRepository.save(new Conversation());

        Ticket ticket1 = new Ticket();
        ticket1.setConversation(conversationA);
        ticket1.setStatus(TicketStatus.OPEN);
        ticketRepository.save(ticket1);

        Ticket ticket2 = new Ticket();
        ticket2.setConversation(conversationA);
        ticket2.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticket2);

        Ticket ticket3 = new Ticket();
        ticket3.setConversation(conversationB);
        ticket3.setStatus(TicketStatus.OPEN);
        ticketRepository.save(ticket3);

        restTestClient.get().uri("/tickets?status=OPEN")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TicketResponse>>() {})
                .value(tickets -> {
                    assertThat(tickets).allSatisfy(t ->
                            assertThat(t.status()).isEqualTo("OPEN")
                    );
                    List<Long> ids = tickets.stream().map(TicketResponse::id).toList();
                    // Expect newest first among the two OPEN tickets this test created.
                    assertThat(ids).containsSubsequence(ticket3.getId(), ticket1.getId());
                    assertThat(ids).doesNotContain(ticket2.getId());
                });
    }

    @Test
    @Order(52)
    void getAllTickets_withStatusFilterInProgress_returns200WithOnlyInProgressTickets() {
        Conversation conversation = new Conversation();
        Conversation savedConv = conversationRepository.save(conversation);

        Ticket ticket1 = new Ticket();
        ticket1.setConversation(savedConv);
        ticket1.setStatus(TicketStatus.OPEN);
        ticketRepository.save(ticket1);

        Ticket ticket2 = new Ticket();
        ticket2.setConversation(savedConv);
        ticket2.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(ticket2);

        restTestClient.get().uri("/tickets?status=IN_PROGRESS")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TicketResponse>>() {})
                .value(tickets -> {
                    assertThat(tickets).allSatisfy(t ->
                            assertThat(t.status()).isEqualTo("IN_PROGRESS")
                    );
                    assertThat(tickets).extracting(TicketResponse::id).contains(ticket2.getId());
                    assertThat(tickets).extracting(TicketResponse::id).doesNotContain(ticket1.getId());
                });
    }

    @Test
    @Order(53)
    void getAllTickets_withInvalidStatusValue_returns400() {
        restTestClient.get().uri("/tickets?status=NOT_A_REAL_STATUS")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
