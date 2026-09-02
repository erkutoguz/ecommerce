package dev.erkut.orderservice.order.application;

import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTransactionalServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderTransactionalService orderTransactionalService;

    @Test
    void saveOrder_savesAndReturnsRepositoryResultWithoutExtraInteractions() {
        Order order = sampleOrder();
        Order savedOrder = sampleOrder();
        when(orderRepository.save(order)).thenReturn(savedOrder);

        Order result = orderTransactionalService.saveOrder(order);

        assertSame(savedOrder, result);
        verify(orderRepository).save(order);
        verifyNoMoreInteractions(orderRepository);
    }

    private static Order sampleOrder() {
        return Order.create(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                Currency.TRY,
                List.of(new OrderLineSnapshot(
                        UUID.fromString("90000000-0000-0000-0000-000000000001"),
                        "Product A",
                        new BigDecimal("100.00"),
                        1
                )),
                Instant.parse("2026-01-01T10:00:00Z")
        );
    }
}
