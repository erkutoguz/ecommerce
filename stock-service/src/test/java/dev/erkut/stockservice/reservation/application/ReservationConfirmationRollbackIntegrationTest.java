package dev.erkut.stockservice.reservation.application;

import dev.erkut.stockservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ConfirmStockReservationCommand;
import dev.erkut.stockservice.outbox.application.OutboxService;
import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.stockservice.reservation.domain.Reservation;
import dev.erkut.stockservice.reservation.domain.ReservationStatus;
import dev.erkut.stockservice.reservation.persistence.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class ReservationConfirmationRollbackIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private InboxMessageRepository inboxRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    @MockitoBean
    private OutboxService outboxService;

    @Test
    void outboxFailureRollsBackInboxAndReservationConfirmation() {
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        reservationRepository.saveAndFlush(Reservation.create(orderId, CREATED_AT));
        doThrow(new RuntimeException("outbox persistence failure"))
                .when(outboxService).createStockReservationConfirmedEvent(any(), any());

        assertThrows(RuntimeException.class, () -> reservationService
                .handleConfirmStockReservationCommand(
                        new MessageEnvelope(
                                messageId,
                                "CONFIRM_STOCK_RESERVATION_COMMAND",
                                CREATED_AT,
                                new tools.jackson.databind.json.JsonMapper().createObjectNode()
                        ),
                        new ConfirmStockReservationCommand(orderId)
                ));

        assertFalse(inboxRepository.existsById(messageId));
        assertEquals(ReservationStatus.RESERVED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(0, outboxRepository.count());
    }
}
