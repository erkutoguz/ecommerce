package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.inbox.application.InboxService;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.command.OrderRejectionReason;
import dev.erkut.orderworkflowservice.message.command.ProcessPaymentCommand;
import dev.erkut.orderworkflowservice.message.command.RejectOrderCommand;
import dev.erkut.orderworkflowservice.message.command.ReserveStockCommand;
import dev.erkut.orderworkflowservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.message.event.OrderRejectedEvent;
import dev.erkut.orderworkflowservice.message.event.StockReservationFailedEvent;
import dev.erkut.orderworkflowservice.message.event.StockReservedEvent;
import dev.erkut.orderworkflowservice.outbox.application.OutboxService;
import dev.erkut.orderworkflowservice.saga.application.exception.OrderSagaNotFoundException;
import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
import dev.erkut.orderworkflowservice.saga.persistence.OrderSagaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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

        validateCheckoutItems(event);

        Instant now = Instant.now();

        if (isDuplicate(envelope, event.orderId(), now)) {
            return;
        }

        OrderSaga orderSaga = OrderSaga.start(
                event.orderId(),
                event.totalAmount(),
                CurrencyMapper.from(event.currency()),
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

    private void validateCheckoutItems(OrderCheckoutStartedEvent event) {
        if (event.items() == null || event.items().isEmpty()) {
            throw new IllegalArgumentException("Checkout items cannot be null or empty");
        }

        Set<UUID> productIds = new HashSet<>();
        for (OrderCheckoutStartedEvent.OrderCheckoutItem item : event.items()) {
            if (item == null) {
                throw new IllegalArgumentException("Checkout item cannot be null");
            }

            if (item.productId() == null) {
                throw new IllegalArgumentException("Product id cannot be null");
            }

            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero");
            }

            if (!productIds.add(item.productId())) {
                throw new IllegalArgumentException("Duplicate product id: " + item.productId());
            }
        }
    }

    @Transactional
    public void handleStockReservationFailedEvent(MessageEnvelope envelope, StockReservationFailedEvent event) {
        validateStockEvent(envelope, event);

        Instant now = Instant.now();

        if (isDuplicate(envelope, event.orderId(), now)) {
            return;
        }

        OrderSaga orderSaga = orderSagaRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderSagaNotFoundException("Order saga is not found with id: " + event.orderId()));

        orderSaga.markOrderRejectionPending(now);
        OrderRejectionReason reason = OrderRejectionReasonMapper.from(event.reason());
        RejectOrderCommand command = new RejectOrderCommand(event.orderId(), reason);

        outboxService.createRejectOrderCommand(command, now);
    }

    @Transactional
    public void handleStockReservedEvent(MessageEnvelope envelope, StockReservedEvent event) {
        validateStockEvent(envelope, event);
        Instant now = Instant.now();

        if (isDuplicate(envelope, event.orderId(), now)) {
            return;
        }

        OrderSaga orderSaga = orderSagaRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderSagaNotFoundException("Order saga is not found with id: " + event.orderId()));

        orderSaga.markPaymentPending(now);
        ProcessPaymentCommand command =
                new ProcessPaymentCommand(
                        orderSaga.getOrderId(),
                        orderSaga.getTotalAmount(),
                        CurrencyMapper.toCommand(orderSaga.getCurrency())
                );

        outboxService.handleProcessPaymentCommand(command, now);

    }

    @Transactional
    public void handleOrderRejectedEvent(MessageEnvelope envelope, OrderRejectedEvent event) {
        validateOrderEvent(envelope, event);

        Instant now = Instant.now();

        if(isDuplicate(envelope, event.orderId(), now)) {
            return;
        }

        OrderSaga orderSaga = orderSagaRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderSagaNotFoundException("Order saga is not found with id: " + event.orderId()));

        orderSaga.markFailed(now);
    }

    private void validateStockEvent(MessageEnvelope envelope, Object event) {
        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        if (event == null) {
            throw new IllegalArgumentException("Stock event cannot be null");
        }

    }

    private void validateOrderEvent(MessageEnvelope envelope, Object event) {
        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        if (event == null) {
            throw new IllegalArgumentException("Order event cannot be null");
        }

    }

    private boolean isDuplicate(
            MessageEnvelope envelope,
            UUID orderId,
            Instant now
    ) {
        return !inboxService.tryRegister(
                envelope.messageId(),
                envelope.messageType(),
                orderId,
                now
        );
    }


}
