package dev.erkut.orderservice.service;

import dev.erkut.orderservice.model.Currency;
import dev.erkut.orderservice.model.Order;
import dev.erkut.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

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
        Order order = Order.create(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                Currency.TRY,
                Instant.parse("2026-01-01T10:00:00Z"));
        Order savedOrder = Order.create(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                Currency.TRY,
                Instant.parse("2026-01-01T10:00:00Z"));
        when(orderRepository.save(order)).thenReturn(savedOrder);

        Order result = orderTransactionalService.saveOrder(order);

        assertSame(savedOrder, result);
        verify(orderRepository).save(order);
        verifyNoMoreInteractions(orderRepository);
    }
}
