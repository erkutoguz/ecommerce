package dev.erkut.orderservice.order.application;

import dev.erkut.orderservice.integration.customer.CustomerClient;
import dev.erkut.orderservice.integration.customer.CustomerLookupResponse;
import dev.erkut.orderservice.integration.customer.CustomerNotFoundException;
import dev.erkut.orderservice.integration.customer.CustomerStatus;
import dev.erkut.orderservice.integration.customer.InvalidCustomerStateException;
import dev.erkut.orderservice.integration.product.InvalidProductStateException;
import dev.erkut.orderservice.integration.product.ProductClient;
import dev.erkut.orderservice.integration.product.ProductLookupRequest;
import dev.erkut.orderservice.integration.product.ProductLookupResponse;
import dev.erkut.orderservice.integration.product.ProductNotFoundException;
import dev.erkut.orderservice.integration.product.ProductServiceUnavailableException;
import dev.erkut.orderservice.integration.product.ProductStatus;
import dev.erkut.orderservice.order.api.request.OrderCreateRequest;
import dev.erkut.orderservice.order.api.request.OrderItemRequest;
import dev.erkut.orderservice.order.api.request.OrderItemUpdateRequest;
import dev.erkut.orderservice.order.api.response.OrderResponse;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.exception.InvalidOrderStateException;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID KNOWN_CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID UNKNOWN_CUSTOMER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID KNOWN_PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID UNKNOWN_PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000099");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T10:05:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private OrderTransactionalService orderTransactionalService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_activeCustomerAndProducts_shouldBulkLookupAndDelegateAfterValidation() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(
                KNOWN_CUSTOMER_ID,
                Currency.TRY,
                List.of(new OrderItemRequest(KNOWN_PRODUCT_ID, 2))
        );
        when(customerClient.getCustomerDetail(KNOWN_CUSTOMER_ID))
                .thenReturn(new CustomerLookupResponse(KNOWN_CUSTOMER_ID, CustomerStatus.ACTIVE));
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenReturn(List.of(activeProduct(KNOWN_PRODUCT_ID, "Mechanical Keyboard", "2500.00")));
        when(orderTransactionalService.saveOrder(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<ProductLookupRequest> lookupCaptor =
                ArgumentCaptor.forClass(ProductLookupRequest.class);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        verify(productClient).getProductsByIds(lookupCaptor.capture());
        assertEquals(List.of(KNOWN_PRODUCT_ID), lookupCaptor.getValue().requestedProductIds());
        verify(orderTransactionalService).saveOrder(orderCaptor.capture());
        assertEquals(1, orderCaptor.getValue().getItems().size());
        assertEquals(2, orderCaptor.getValue().getItems().getFirst().getQuantity());
        assertEquals(2, response.items().getFirst().quantity());
        verify(orderRepository, never()).save(any(Order.class));
        var order = inOrder(customerClient, productClient, orderTransactionalService);
        order.verify(customerClient).getCustomerDetail(request.customerId());
        order.verify(productClient).getProductsByIds(any(ProductLookupRequest.class));
        order.verify(orderTransactionalService).saveOrder(any(Order.class));
    }

    @Test
    void createOrder_inactiveCustomer_shouldThrowInvalidCustomerStateExceptionWithoutSaving() {
        OrderCreateRequest request = new OrderCreateRequest(
                KNOWN_CUSTOMER_ID,
                Currency.TRY,
                List.of(new OrderItemRequest(KNOWN_PRODUCT_ID, 1))
        );
        when(customerClient.getCustomerDetail(KNOWN_CUSTOMER_ID))
                .thenReturn(new CustomerLookupResponse(KNOWN_CUSTOMER_ID, CustomerStatus.INACTIVE));

        assertThrows(InvalidCustomerStateException.class, () -> orderService.createOrder(request));

        verify(customerClient).getCustomerDetail(request.customerId());
        verify(productClient, never()).getProductsByIds(any(ProductLookupRequest.class));
        verify(orderTransactionalService, never()).saveOrder(any(Order.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_duplicateProducts_shouldLookupUniqueIdsAndNormalizeQuantities() {
        OrderCreateRequest request = new OrderCreateRequest(
                KNOWN_CUSTOMER_ID,
                Currency.TRY,
                List.of(
                        new OrderItemRequest(KNOWN_PRODUCT_ID, 2),
                        new OrderItemRequest(SECOND_PRODUCT_ID, 1),
                        new OrderItemRequest(KNOWN_PRODUCT_ID, 3)
                )
        );
        when(customerClient.getCustomerDetail(KNOWN_CUSTOMER_ID))
                .thenReturn(new CustomerLookupResponse(KNOWN_CUSTOMER_ID, CustomerStatus.ACTIVE));
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenReturn(List.of(
                        activeProduct(SECOND_PRODUCT_ID, "Wireless Mouse", "900.00"),
                        activeProduct(KNOWN_PRODUCT_ID, "Mechanical Keyboard", "2500.00")
                ));
        when(orderTransactionalService.saveOrder(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<ProductLookupRequest> lookupCaptor =
                ArgumentCaptor.forClass(ProductLookupRequest.class);

        OrderResponse response = orderService.createOrder(request);

        verify(productClient).getProductsByIds(lookupCaptor.capture());
        assertEquals(2, lookupCaptor.getValue().requestedProductIds().size());
        assertEquals(
                java.util.Set.of(KNOWN_PRODUCT_ID, SECOND_PRODUCT_ID),
                java.util.Set.copyOf(lookupCaptor.getValue().requestedProductIds()));
        assertEquals(2, response.items().size());
        assertEquals(5, response.items().stream()
                .filter(item -> item.itemId().equals(KNOWN_PRODUCT_ID))
                .findFirst().orElseThrow().quantity());
        assertEquals(1, response.items().stream()
                .filter(item -> item.itemId().equals(SECOND_PRODUCT_ID))
                .findFirst().orElseThrow().quantity());
        verify(orderTransactionalService).saveOrder(any(Order.class));
    }

    @Test
    void createOrder_inactiveProduct_shouldThrowWithoutSaving() {
        OrderCreateRequest request = new OrderCreateRequest(
                KNOWN_CUSTOMER_ID, Currency.TRY,
                List.of(new OrderItemRequest(KNOWN_PRODUCT_ID, 1)));
        when(customerClient.getCustomerDetail(KNOWN_CUSTOMER_ID))
                .thenReturn(new CustomerLookupResponse(KNOWN_CUSTOMER_ID, CustomerStatus.ACTIVE));
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenReturn(List.of(
                        activeProduct(KNOWN_PRODUCT_ID, "Keyboard", "2500.00"),
                        new ProductLookupResponse(SECOND_PRODUCT_ID, "Mouse", new BigDecimal("900.00"), ProductStatus.INACTIVE)));

        assertThrows(InvalidProductStateException.class, () -> orderService.createOrder(request));

        verify(orderTransactionalService, never()).saveOrder(any(Order.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_productNotFoundFromClient_shouldPropagateWithoutSaving() {
        OrderCreateRequest request = new OrderCreateRequest(
                KNOWN_CUSTOMER_ID, Currency.TRY,
                List.of(new OrderItemRequest(UNKNOWN_PRODUCT_ID, 1)));
        when(customerClient.getCustomerDetail(KNOWN_CUSTOMER_ID))
                .thenReturn(new CustomerLookupResponse(KNOWN_CUSTOMER_ID, CustomerStatus.ACTIVE));
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenThrow(new ProductNotFoundException("Product(s) not found"));

        assertThrows(ProductNotFoundException.class, () -> orderService.createOrder(request));

        verify(orderTransactionalService, never()).saveOrder(any(Order.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_productServiceUnavailableFromClient_shouldPropagateWithoutSaving() {
        OrderCreateRequest request = new OrderCreateRequest(
                KNOWN_CUSTOMER_ID, Currency.TRY,
                List.of(new OrderItemRequest(KNOWN_PRODUCT_ID, 1)));
        when(customerClient.getCustomerDetail(KNOWN_CUSTOMER_ID))
                .thenReturn(new CustomerLookupResponse(KNOWN_CUSTOMER_ID, CustomerStatus.ACTIVE));
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenThrow(new ProductServiceUnavailableException("Product service unavailable"));

        assertThrows(ProductServiceUnavailableException.class, () -> orderService.createOrder(request));

        verify(orderTransactionalService, never()).saveOrder(any(Order.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_unknownCustomer_shouldThrowCustomerNotFoundException() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(
                UNKNOWN_CUSTOMER_ID,
                Currency.TRY,
                List.of(new OrderItemRequest(KNOWN_PRODUCT_ID, 1))
        );
        when(customerClient.getCustomerDetail(UNKNOWN_CUSTOMER_ID))
                .thenThrow(new CustomerNotFoundException("Customer not found"));

        // Act
        // Assert
        assertThrows(CustomerNotFoundException.class, () -> orderService.createOrder(request));
        verify(customerClient).getCustomerDetail(request.customerId());
        verify(productClient, never()).getProductsByIds(any(ProductLookupRequest.class));
        verify(orderTransactionalService, never()).saveOrder(any(Order.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_unknownProduct_shouldThrowProductNotFoundException() {
        // Arrange
        OrderCreateRequest request = new OrderCreateRequest(
                KNOWN_CUSTOMER_ID,
                Currency.TRY,
                List.of(new OrderItemRequest(UNKNOWN_PRODUCT_ID, 1))
        );
        when(customerClient.getCustomerDetail(KNOWN_CUSTOMER_ID))
                .thenReturn(new CustomerLookupResponse(KNOWN_CUSTOMER_ID, CustomerStatus.ACTIVE));
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenThrow(new ProductNotFoundException("Product(s) not found"));

        // Act
        // Assert
        assertThrows(ProductNotFoundException.class, () -> orderService.createOrder(request));
        verify(productClient).getProductsByIds(any(ProductLookupRequest.class));
        verify(orderTransactionalService, never()).saveOrder(any(Order.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void addOrderItem_validNewItem_shouldAddItemAndReturnUpdatedOrder() {
        // Arrange
        Order order = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 1);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenReturn(List.of(activeProduct(SECOND_PRODUCT_ID, "Wireless Mouse", "900.00")));

        // Act
        OrderResponse response = orderService.addOrderItem(
                ORDER_ID,
                new OrderItemRequest(SECOND_PRODUCT_ID, 2)
        );

        // Assert
        assertEquals(2, response.items().size());
        assertEquals(SECOND_PRODUCT_ID, response.items().get(1).itemId());
        assertEquals(2, response.items().get(1).quantity());
        assertEquals(new BigDecimal("4300.00"), response.totalAmount());
        assertTrue(response.updatedAt().isAfter(UPDATED_AT));
        verify(orderRepository).findById(ORDER_ID);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void addOrderItem_existingItem_shouldIncreaseQuantityWithoutDuplicate() {
        // Arrange
        Order order = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 2);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenReturn(List.of(activeProduct(KNOWN_PRODUCT_ID, "Mechanical Keyboard", "2500.00")));

        // Act
        OrderResponse response = orderService.addOrderItem(
                ORDER_ID,
                new OrderItemRequest(KNOWN_PRODUCT_ID, 3)
        );

        // Assert
        assertEquals(1, response.items().size());
        assertEquals(5, response.items().get(0).quantity());
        assertEquals(new BigDecimal("12500.00"), response.totalAmount());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void addOrderItem_unknownProduct_shouldThrowProductNotFoundException() {
        // Arrange
        Order order = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 1);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenThrow(new ProductNotFoundException("Product not found: " + UNKNOWN_PRODUCT_ID));

        // Act
        // Assert
        assertThrows(
                ProductNotFoundException.class,
                () -> orderService.addOrderItem(
                        ORDER_ID,
                        new OrderItemRequest(UNKNOWN_PRODUCT_ID, 1)
                )
        );
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void addOrderItem_nonPendingOrder_shouldThrowInvalidOrderStateException() {
        // Arrange
        Order order = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 1);
        order.confirm(UPDATED_AT);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenReturn(List.of(activeProduct(SECOND_PRODUCT_ID, "Wireless Mouse", "900.00")));

        // Act
        // Assert
        assertThrows(
                InvalidOrderStateException.class,
                () -> orderService.addOrderItem(
                        ORDER_ID,
                        new OrderItemRequest(SECOND_PRODUCT_ID, 1)
                )
        );
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderItem_validRequest_shouldReturnUpdatedOrder() {
        // Arrange
        Order order = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 2);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // Act
        var response = orderService.updateOrderItem(
                ORDER_ID,
                KNOWN_PRODUCT_ID,
                new OrderItemUpdateRequest(3)
        );

        // Assert
        assertEquals(3, response.items().get(0).quantity());
        assertEquals(new BigDecimal("7500.00"), response.totalAmount());
        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    void removeOrderItem_validRequest_shouldReturnUpdatedOrder() {
        // Arrange
        Order order = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 1);
        order.addItem(SECOND_PRODUCT_ID, "Wireless Mouse", new BigDecimal("900.00"), 2, UPDATED_AT);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // Act
        var response = orderService.removeOrderItem(ORDER_ID, KNOWN_PRODUCT_ID);

        // Assert
        assertEquals(1, response.items().size());
        assertEquals(SECOND_PRODUCT_ID, response.items().get(0).itemId());
        assertEquals(new BigDecimal("1800.00"), response.totalAmount());
        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    void getOrders_withoutCustomerId_shouldReturnPaginatedOrders() {
        // Arrange
        Order firstOrder = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 1);
        Order secondOrder = orderWithItem(KNOWN_CUSTOMER_ID, SECOND_PRODUCT_ID, 2);
        Pageable requestedPage = org.springframework.data.domain.PageRequest.of(1, 2);
        Page<Order> orders = new PageImpl<>(List.of(firstOrder, secondOrder), requestedPage, 5);
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(orders);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        // Act
        Page<OrderResponse> response = orderService.getOrders(null, 1, 2);

        // Assert
        assertEquals(5, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertEquals(2, response.getContent().size());
        verify(orderRepository).findAll(pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(2, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("id").getDirection());
        verify(orderRepository, never()).findAllByCustomerId(any(UUID.class), any(Pageable.class));
    }

    @Test
    void getOrders_withCustomerId_shouldReturnFilteredPaginatedOrders() {
        // Arrange
        Order order = orderWithItem(KNOWN_CUSTOMER_ID, KNOWN_PRODUCT_ID, 1);
        Page<Order> orders = new PageImpl<>(List.of(order), org.springframework.data.domain.PageRequest.of(0, 2), 1);
        when(orderRepository.findAllByCustomerId(any(UUID.class), any(Pageable.class))).thenReturn(orders);
        ArgumentCaptor<UUID> customerCaptor = ArgumentCaptor.forClass(UUID.class);

        // Act
        Page<OrderResponse> response =
                orderService.getOrders(KNOWN_CUSTOMER_ID, 0, 2);

        // Assert
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        verify(orderRepository).findAllByCustomerId(customerCaptor.capture(), any(Pageable.class));
        assertEquals(KNOWN_CUSTOMER_ID, customerCaptor.getValue());
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }

    private Order orderWithItem(UUID customerId, UUID itemId, int quantity) {
        Order order = Order.create(customerId, Currency.TRY, CREATED_AT);
        order.addItem(itemId, "Test Product", new BigDecimal("2500.00"), quantity, UPDATED_AT);
        return order;
    }

    private ProductLookupResponse activeProduct(UUID productId, String name, String price) {
        return new ProductLookupResponse(productId, name, new BigDecimal(price), ProductStatus.ACTIVE);
    }
}
