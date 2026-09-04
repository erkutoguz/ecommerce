package dev.erkut.orderservice.checkout.application;

import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartStatus;
import dev.erkut.orderservice.cart.persistence.CartRepository;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import dev.erkut.orderservice.order.domain.OrderStatus;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import dev.erkut.orderservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderservice.outbox.domain.OutboxStatus;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.jpa.properties.hibernate.type.json_format_mapper=jackson3")
class CheckoutTransactionalServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa16");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000016");
    private static final Instant CHECKOUT_AT = Instant.parse("2026-01-01T10:05:00Z");

    @Autowired
    private CheckoutTransactionalService checkoutTransactionalService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void checkout_shouldPersistCartOrderAndOutboxAtomically() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID cartId = seedCart(transactionTemplate);

        Order createdOrder = checkoutTransactionalService.checkout(
                cartId,
                0,
                Currency.TRY,
                List.of(new OrderLineSnapshot(PRODUCT_ID, "Product 16", new BigDecimal("100.00"), 2)),
                CHECKOUT_AT
        );

        Cart cart = transactionTemplate.execute(status -> cartRepository.findWithCartItemsById(cartId).orElseThrow());
        Order order = transactionTemplate.execute(status -> orderRepository.findWithItemsById(createdOrder.getId()).orElseThrow());
        OutboxMessage outbox = transactionTemplate.execute(status ->
                outboxMessageRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).stream()
                        .filter(message -> message.getAggregateId().equals(createdOrder.getId()))
                        .findFirst()
                        .orElseThrow()
        );
        JsonNode payload = outbox.getPayload();

        assertEquals(CartStatus.CHECKOUT_LOCKED, cart.getStatus());
        assertEquals(OrderStatus.PENDING_STOCK, order.getStatus());
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());
        assertEquals(OutboxMessageType.ORDER_CHECKOUT_STARTED, outbox.getMessageType());
        assertEquals(createdOrder.getId(), outbox.getAggregateId());
        assertEquals(createdOrder.getId().toString(), payload.get("orderId").asText());
        assertEquals(PRODUCT_ID.toString(), payload.get("items").get(0).get("productId").asText());
        assertEquals(2, payload.get("items").get(0).get("quantity").asInt());
    }

    private UUID seedCart(TransactionTemplate transactionTemplate) {
        return transactionTemplate.execute(status -> {
            Cart cart = Cart.create(CUSTOMER_ID, CHECKOUT_AT.minusSeconds(60));
            cart.addCartItem(PRODUCT_ID, 2, CHECKOUT_AT.minusSeconds(30));
            return cartRepository.save(cart).getId();
        });
    }
}
