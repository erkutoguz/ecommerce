package dev.erkut.stockservice.reservation.application;

import dev.erkut.stockservice.inbox.application.InboxService;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.outbox.application.OutboxService;
import dev.erkut.stockservice.reservation.persistence.ReservationRepository;
import dev.erkut.stockservice.stock.application.StockService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationServiceValidationTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private InboxService inboxService;

    @Mock
    private StockService stockService;

    @Mock
    private OutboxService outboxService;

    @ParameterizedTest
    @MethodSource("malformedCommands")
    void malformedCommandIsRejectedBeforeAnyPersistence(
            ReserveStockCommand command
    ) {
        ReservationService service = new ReservationService(
                reservationRepository,
                inboxService,
                stockService,
                outboxService
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.handleReserveStock(envelope(), command));

        verifyNoInteractions(
                reservationRepository,
                inboxService,
                stockService,
                outboxService
        );
    }

    private static Stream<Arguments> malformedCommands() {
        return Stream.of(
                Arguments.of((ReserveStockCommand) null),
                Arguments.of(new ReserveStockCommand(null, items(1))),
                Arguments.of(new ReserveStockCommand(ORDER_ID, List.of())),
                Arguments.of(new ReserveStockCommand(ORDER_ID, null)),
                Arguments.of(new ReserveStockCommand(ORDER_ID,
                        Collections.singletonList(null))),
                Arguments.of(new ReserveStockCommand(ORDER_ID,
                        List.of(new ReserveStockCommand.ReserveStockItem(null, 1)))),
                Arguments.of(new ReserveStockCommand(ORDER_ID, items(0))),
                Arguments.of(new ReserveStockCommand(ORDER_ID, items(-1))),
                Arguments.of(new ReserveStockCommand(ORDER_ID, List.of(
                        new ReserveStockCommand.ReserveStockItem(PRODUCT_ID, 2),
                        new ReserveStockCommand.ReserveStockItem(PRODUCT_ID, 3)
                )))
        );
    }

    private static List<ReserveStockCommand.ReserveStockItem> items(int quantity) {
        return List.of(new ReserveStockCommand.ReserveStockItem(PRODUCT_ID, quantity));
    }

    private static MessageEnvelope envelope() {
        return new MessageEnvelope(
                UUID.randomUUID(),
                "RESERVE_STOCK_COMMAND",
                Instant.parse("2026-01-01T10:00:00Z"),
                new tools.jackson.databind.json.JsonMapper().createObjectNode()
        );
    }
}
