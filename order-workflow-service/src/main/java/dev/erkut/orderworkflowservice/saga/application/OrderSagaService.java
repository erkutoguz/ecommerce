package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.inbox.application.InboxService;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.command.*;
import dev.erkut.orderworkflowservice.message.command.ordercommands.*;
import dev.erkut.orderworkflowservice.message.command.stockcommands.ConfirmStockReservationCommand;
import dev.erkut.orderworkflowservice.message.command.stockcommands.ReleaseStockReservationCommand;
import dev.erkut.orderworkflowservice.message.command.stockcommands.ReserveStockCommand;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderConfirmedEvent;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderRejectedEvent;
import dev.erkut.orderworkflowservice.message.event.paymentevents.PaymentCompletedEvent;
import dev.erkut.orderworkflowservice.message.event.paymentevents.PaymentFailedEvent;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationConfirmedEvent;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationFailedEvent;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationReleasedEvent;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservedEvent;
import dev.erkut.orderworkflowservice.outbox.application.OutboxService;
import dev.erkut.orderworkflowservice.saga.application.exception.OrderSagaNotFoundException;
import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
import dev.erkut.orderworkflowservice.saga.domain.OrderSagaFailureReason;
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
        MarkOrderStockReservedCommand markOrderStockReservedCommand =
                new MarkOrderStockReservedCommand(event.orderId());
        InitiatePaymentCommand command =
                new InitiatePaymentCommand(
                        orderSaga.getOrderId(),
                        orderSaga.getTotalAmount(),
                        CurrencyMapper.toCommand(orderSaga.getCurrency())
                );

        outboxService.createMarkOrderStockReservedCommand(markOrderStockReservedCommand, now);
        outboxService.handleInitiatePaymentCommand(command, now);

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

    @Transactional
    public void handlePaymentCompletedEvent(MessageEnvelope envelope, PaymentCompletedEvent event) {
        validatePaymentEvent(envelope, event);

        Instant now = Instant.now();

        if(isDuplicate(envelope, event.orderId(), now)) {
            return;
        }

        OrderSaga orderSaga = orderSagaRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderSagaNotFoundException("Order saga is not found with id: " + event.orderId()));

        orderSaga.markStockReservationConfirmationPending(now);

        MarkOrderPaymentCompletedCommand markOrderPaymentCompletedCommand =
                new MarkOrderPaymentCompletedCommand(event.orderId());
        ConfirmStockReservationCommand command = new ConfirmStockReservationCommand(event.orderId());
        outboxService.createMarkOrderPaymentCompletedCommand(markOrderPaymentCompletedCommand, now);
        outboxService.handleConfirmStockReservationCommand(command, now);
    }

    @Transactional
    public void handleStockReservationConfirmedEvent(MessageEnvelope envelope, StockReservationConfirmedEvent event) {
        validateStockEvent(envelope, event);

        Instant now = Instant.now();

        if(isDuplicate(envelope, event.orderId(), now)) {
            return;
        }

        OrderSaga orderSaga = orderSagaRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderSagaNotFoundException("Order saga is not found with id: " + event.orderId()));

        orderSaga.markOrderConfirmationPending(now);

        ConfirmOrderCommand command = new ConfirmOrderCommand(event.orderId());
        outboxService.handleConfirmOrderCommand(command, now);
    }

    @Transactional
    public void handleStockReservationReleasedEvent(
            MessageEnvelope envelope,
            StockReservationReleasedEvent event
    ) {
        validateStockEvent(envelope, event);

        Instant now = Instant.now();

        if (isDuplicate(envelope, event.orderId(), now)) {
            return;
        }

        OrderSaga orderSaga = orderSagaRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderSagaNotFoundException(
                        "Order saga is not found with id: " + event.orderId()
                ));

        orderSaga.markOrderRejectionPendingAfterStockRelease(now);
        OrderRejectionReason reason = OrderRejectionReasonMapper.from(orderSaga.getFailureReason());
        RejectOrderCommand command = new RejectOrderCommand(event.orderId(), reason);
        outboxService.createRejectOrderCommand(command, now);
    }

    @Transactional
    public void handleOrderConfirmedEvent(MessageEnvelope envelope, OrderConfirmedEvent event) {
        validateOrderEvent(envelope, event);
        Instant now = Instant.now();

        if(isDuplicate(envelope, event.orderId(), now)) {
            return;
        }

        OrderSaga orderSaga = orderSagaRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderSagaNotFoundException("Order saga is not found with id: " + event.orderId()));

        orderSaga.markCompleted(now);
    }

    @Transactional
    public void handlePaymentFailedEvent(MessageEnvelope envelope, PaymentFailedEvent event) {
        validatePaymentEvent(envelope, event);

        Instant now = Instant.now();

        if(isDuplicate(envelope, event.orderId(), now)) {
            return;
        }

        OrderSaga orderSaga = orderSagaRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderSagaNotFoundException("Order saga is not found with id: " + event.orderId()));

        OrderSagaFailureReason failureReason = OrderRejectionReasonMapper.from(event.failureReason());
        orderSaga.markStockReservationReleasePending(now, failureReason);

        ReleaseStockReservationCommand command = new ReleaseStockReservationCommand(event.orderId());
        outboxService.createReleaseStockReservationCommand(command, now);
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

    private void validatePaymentEvent(MessageEnvelope envelope, Object event) {
        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        if (event == null) {
            throw new IllegalArgumentException("Payment event cannot be null");
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
