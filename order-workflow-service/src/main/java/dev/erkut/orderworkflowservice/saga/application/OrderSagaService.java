package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.inbox.application.InboxService;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.command.ReserveStockCommand;
import dev.erkut.orderworkflowservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.outbox.application.OutboxService;
import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
import dev.erkut.orderworkflowservice.saga.persistence.OrderSagaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OrderSagaService {

    private final InboxService inboxService;
    private final OrderSagaRepository orderSagaRepository;
    private final OutboxService outboxService;

    public OrderSagaService(
            InboxService inboxService,
            OrderSagaRepository orderSagaRepository,
            OutboxService outboxService
    ) {
        this.inboxService = inboxService;
        this.orderSagaRepository = orderSagaRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public void handleOrderCheckoutStarted(
            MessageEnvelope envelope,
            OrderCheckoutStartedEvent event) {

        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        if (event == null) {
            throw new IllegalArgumentException("Order checkout started event cannot be null");
        }

        Instant now = Instant.now();

        boolean registered = inboxService.tryRegister(
                envelope.messageId(),
                envelope.messageType(),
                event.orderId(),
                now
        );
        if (!registered) {
            return;
        }

        OrderSaga orderSaga = OrderSaga.start(
                event.orderId(),
                event.customerId(),
                now
        );

        ReserveStockCommand command = new ReserveStockCommand(
                event.orderId(),
                event.items().stream().map(item ->
                    new ReserveStockCommand.ReserveStockItem(item.productId(), item.quantity())
                ).toList()
        );

        orderSagaRepository.save(orderSaga);
        outboxService.createReserveStockCommand(command, now);
    }
}
