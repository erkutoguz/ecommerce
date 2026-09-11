package dev.erkut.orderservice.order.application;

import dev.erkut.orderservice.inbox.application.InboxService;
import dev.erkut.orderservice.cart.application.exception.CartNotFoundException;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.persistence.CartRepository;
import dev.erkut.orderservice.message.MessageEnvelope;
import dev.erkut.orderservice.message.command.ConfirmOrderCommand;
import dev.erkut.orderservice.message.command.MarkOrderPaymentCompletedCommand;
import dev.erkut.orderservice.message.command.MarkOrderStockReservedCommand;
import dev.erkut.orderservice.message.command.RejectOrderCommand;
import dev.erkut.orderservice.message.event.OrderConfirmedEvent;
import dev.erkut.orderservice.message.event.OrderRejectedEvent;
import dev.erkut.orderservice.order.api.OrderMapper;
import dev.erkut.orderservice.order.api.response.OrderResponse;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import dev.erkut.orderservice.order.domain.OrderRejectionReason;
import dev.erkut.orderservice.order.domain.exception.OrderNotFoundException;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.erkut.orderservice.outbox.application.OutboxService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final InboxService inboxService;
    private final OutboxService outboxService;
    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            InboxService inboxService, OutboxService outboxService
    ) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.inboxService = inboxService;
        this.outboxService = outboxService;
    }

    @Transactional
    public Order createFromCheckout(
            UUID sourceCartId,
            UUID customerId,
            Currency currency,
            List<OrderLineSnapshot> itemSnapshots,
            Instant now
    ) {
        Order order = Order.create(sourceCartId, customerId, currency, itemSnapshots, now);
        return orderRepository.save(order);
    }

    @Transactional
    public void handleRejectOrderCommand(MessageEnvelope envelope, RejectOrderCommand command) {
        validateOrderCommand(envelope, command);

        Instant now = Instant.now();

        if (isDuplicate(envelope, command.orderId(), now)) {
            return;
        }

        Order order = reject(command.orderId(), command.rejectionReason(), now);
        Cart cart = cartRepository.findById(order.getSourceCartId())
                .orElseThrow(() -> new CartNotFoundException(
                        "Cart not found with id: " + order.getSourceCartId()
                ));
        cart.reopen(now);

        OrderRejectedEvent event = new OrderRejectedEvent(command.orderId());
        outboxService.createOrderRejectedEvent(event, now);
    }

    @Transactional
    public void handleConfirmOrderCommand(MessageEnvelope envelope, ConfirmOrderCommand command) {
        validateOrderCommand(envelope, command);

        Instant now = Instant.now();

        if (isDuplicate(envelope, command.orderId(), now)) {
            return;
        }

        Order order = confirm(command.orderId(), now);
        Cart cart = cartRepository.findById(order.getSourceCartId())
                .orElseThrow(() -> new CartNotFoundException(
                        "Cart not found with id: " + order.getSourceCartId()
                ));
        cart.complete(now);

        OrderConfirmedEvent event = new OrderConfirmedEvent(command.orderId());
        outboxService.createOrderConfirmedEvent(event, now);
    }

    @Transactional
    public void handleMarkOrderStockReservedCommand(
            MessageEnvelope envelope,
            MarkOrderStockReservedCommand command
    ) {
        validateOrderCommand(envelope, command);

        Instant now = Instant.now();

        if (isDuplicate(envelope, command.orderId(), now)) {
            return;
        }

        markStockReserved(command.orderId(), now);
    }

    @Transactional
    public void handleMarkOrderPaymentCompletedCommand(
            MessageEnvelope envelope,
            MarkOrderPaymentCompletedCommand command
    ) {
        validateOrderCommand(envelope, command);

        Instant now = Instant.now();

        if (isDuplicate(envelope, command.orderId(), now)) {
            return;
        }

        markPaymentCompleted(command.orderId(), now);
    }

    @Transactional
    public Order markStockReserved(UUID orderId, Instant now) {
        Order order = findOrderById(orderId);
        order.markStockReserved(now);
        return order;
    }

    @Transactional
    public Order markPaymentUnknown(UUID orderId, Instant now) {
        Order order = findOrderById(orderId);
        order.markPaymentUnknown(now);
        return order;
    }

    @Transactional
    public Order markPaymentCompleted(UUID orderId, Instant now) {
        Order order = findOrderById(orderId);
        order.markPaymentCompleted(now);
        return order;
    }

    @Transactional
    public Order confirm(UUID orderId, Instant now) {
        Order order = findOrderById(orderId);
        order.confirm(now);
        return order;
    }

    @Transactional
    public Order reject(UUID orderId, OrderRejectionReason reason, Instant now) {
        Order order = findOrderById(orderId);
        order.reject(reason, now);
        return order;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(UUID customerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending()
                .and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Order> orders = customerId == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findAllByCustomerId(customerId, pageable);

        return orders.map(OrderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        return OrderMapper.toResponse(order);
    }

    private Order findOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
    }

    private void validateOrderCommand(MessageEnvelope envelope, Object event) {
        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        if (event == null) {
            throw new IllegalArgumentException("Order command cannot be null");
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
