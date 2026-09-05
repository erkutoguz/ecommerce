package dev.erkut.orderservice.cart.application;

import dev.erkut.orderservice.cart.application.exception.CartNotFoundException;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartStatus;
import dev.erkut.orderservice.cart.domain.exception.CartItemNotFoundException;
import dev.erkut.orderservice.cart.persistence.CartRepository;
import dev.erkut.orderservice.integration.customer.CustomerClient;
import dev.erkut.orderservice.integration.customer.CustomerLookupResponse;
import dev.erkut.orderservice.integration.customer.CustomerStatus;
import dev.erkut.orderservice.integration.customer.InvalidCustomerStateException;
import dev.erkut.orderservice.integration.product.InvalidProductStateException;
import dev.erkut.orderservice.integration.product.ProductClient;
import dev.erkut.orderservice.integration.product.ProductLookupRequest;
import dev.erkut.orderservice.integration.product.ProductLookupResponse;
import dev.erkut.orderservice.integration.product.ProductNotFoundException;
import dev.erkut.orderservice.integration.product.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final UUID CART_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_A = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_B = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final UUID PRODUCT_C = UUID.fromString("90000000-0000-0000-0000-000000000003");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartTransactionalService transactionalService;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private CartService cartService;

    @Test
    void createCart_activeCustomer_shouldCreateInsideTransactionalService() {
        when(customerClient.getCustomerDetail(CUSTOMER_ID))
                .thenReturn(new CustomerLookupResponse(CUSTOMER_ID, CustomerStatus.ACTIVE));
        Cart created = Cart.create(CUSTOMER_ID, CREATED_AT);
        when(transactionalService.create(CUSTOMER_ID)).thenReturn(created);

        Cart result = cartService.createCart(CUSTOMER_ID);

        assertSame(created, result);
        InOrder inOrder = inOrder(customerClient, transactionalService);
        inOrder.verify(customerClient).getCustomerDetail(CUSTOMER_ID);
        inOrder.verify(transactionalService).create(CUSTOMER_ID);
    }

    @Test
    void createCart_inactiveCustomer_shouldThrowWithoutCreating() {
        when(customerClient.getCustomerDetail(CUSTOMER_ID))
                .thenReturn(new CustomerLookupResponse(CUSTOMER_ID, CustomerStatus.INACTIVE));

        assertThrows(InvalidCustomerStateException.class, () -> cartService.createCart(CUSTOMER_ID));

        verify(customerClient).getCustomerDetail(CUSTOMER_ID);
        verifyNoInteractions(transactionalService);
    }

    @Test
    void createCart_nullCustomerId_shouldThrowWithoutRemoteCall() {
        assertThrows(IllegalArgumentException.class, () -> cartService.createCart(null));

        verifyNoInteractions(customerClient, transactionalService);
    }

    @Test
    void getOpenCartByCustomerId_activeCart_shouldReturnExistingCart() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        when(cartRepository.findByCustomerIdAndStatusIn(
                CUSTOMER_ID,
                List.of(CartStatus.ACTIVE, CartStatus.CHECKOUT_LOCKED)
        )).thenReturn(Optional.of(cart));

        Cart result = cartService.getOpenCartByCustomerId(CUSTOMER_ID);

        assertSame(cart, result);
        verify(cartRepository).findByCustomerIdAndStatusIn(
                CUSTOMER_ID,
                List.of(CartStatus.ACTIVE, CartStatus.CHECKOUT_LOCKED)
        );
        verifyNoInteractions(customerClient, transactionalService);
    }

    @Test
    void getOpenCartByCustomerId_checkoutLockedCart_shouldReturnExistingCart() {
        Cart cart = checkoutLockedCart();
        when(cartRepository.findByCustomerIdAndStatusIn(
                CUSTOMER_ID,
                List.of(CartStatus.ACTIVE, CartStatus.CHECKOUT_LOCKED)
        )).thenReturn(Optional.of(cart));

        Cart result = cartService.getOpenCartByCustomerId(CUSTOMER_ID);

        assertSame(cart, result);
        assertEquals(CartStatus.CHECKOUT_LOCKED, result.getStatus());
        verifyNoInteractions(customerClient, transactionalService);
    }

    @Test
    void getOpenCartByCustomerId_noOpenCart_shouldCreateCart() {
        when(cartRepository.findByCustomerIdAndStatusIn(
                CUSTOMER_ID,
                List.of(CartStatus.ACTIVE, CartStatus.CHECKOUT_LOCKED)
        )).thenReturn(Optional.empty());
        when(customerClient.getCustomerDetail(CUSTOMER_ID))
                .thenReturn(new CustomerLookupResponse(CUSTOMER_ID, CustomerStatus.ACTIVE));
        Cart created = Cart.create(CUSTOMER_ID, CREATED_AT);
        when(transactionalService.create(CUSTOMER_ID)).thenReturn(created);

        Cart result = cartService.getOpenCartByCustomerId(CUSTOMER_ID);

        assertSame(created, result);
        verify(transactionalService).create(CUSTOMER_ID);
    }

    @Test
    void getOpenCartByCustomerId_nullCustomerId_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> cartService.getOpenCartByCustomerId(null));
        verifyNoInteractions(cartRepository, customerClient, transactionalService);
    }

    @Test
    void getCartById_existingCart_shouldReturnCart() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        when(cartRepository.findWithCartItemsById(CART_ID)).thenReturn(Optional.of(cart));

        Cart result = cartService.getCartById(CART_ID);

        assertSame(cart, result);
    }

    @Test
    void getCartById_missingCart_shouldThrowCartNotFoundException() {
        when(cartRepository.findWithCartItemsById(CART_ID)).thenReturn(Optional.empty());

        assertThrows(CartNotFoundException.class, () -> cartService.getCartById(CART_ID));
    }

    @Test
    void getCartById_nullCartId_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> cartService.getCartById(null));
        verifyNoInteractions(cartRepository);
    }

    @Test
    void addCartItem_activeExistingProduct_shouldCallTransactionalServiceAfterRemoteValidation() {
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenReturn(List.of(activeProduct(PRODUCT_A)));
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        when(transactionalService.addCartItem(eq(CART_ID), eq(PRODUCT_A), eq(2), any(Instant.class)))
                .thenReturn(cart);

        Cart result = cartService.addCartItem(CART_ID, PRODUCT_A, 2);

        assertSame(cart, result);
        InOrder inOrder = inOrder(productClient, transactionalService);
        inOrder.verify(productClient).getProductsByIds(any(ProductLookupRequest.class));
        inOrder.verify(transactionalService).addCartItem(eq(CART_ID), eq(PRODUCT_A), eq(2), any(Instant.class));
    }

    @Test
    void addCartItem_missingProduct_shouldThrowProductNotFoundException() {
        when(productClient.getProductsByIds(any(ProductLookupRequest.class))).thenReturn(List.of());

        assertThrows(ProductNotFoundException.class, () -> cartService.addCartItem(CART_ID, PRODUCT_A, 1));

        verify(productClient).getProductsByIds(any(ProductLookupRequest.class));
        verify(transactionalService, never()).addCartItem(any(), any(), anyInt(), any());
    }

    @Test
    void addCartItem_inactiveProduct_shouldThrowInvalidProductStateException() {
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenReturn(List.of(product(PRODUCT_A, ProductStatus.INACTIVE)));

        assertThrows(InvalidProductStateException.class, () -> cartService.addCartItem(CART_ID, PRODUCT_A, 1));

        verify(transactionalService, never()).addCartItem(any(), any(), anyInt(), any());
    }

    @Test
    void addCartItem_nullProductId_shouldFailWithoutProductClient() {
        assertThrows(IllegalArgumentException.class, () -> cartService.addCartItem(CART_ID, null, 1));

        verifyNoInteractions(productClient, transactionalService);
    }

    @Test
    void addCartItem_nonPositiveQuantity_shouldFailWithoutProductClient() {
        assertThrows(IllegalArgumentException.class, () -> cartService.addCartItem(CART_ID, PRODUCT_A, 0));
        assertThrows(IllegalArgumentException.class, () -> cartService.addCartItem(CART_ID, PRODUCT_A, -1));

        verifyNoInteractions(productClient, transactionalService);
    }

    @Test
    void addCartItem_nullCartId_shouldFailWithoutProductClient() {
        assertThrows(IllegalArgumentException.class, () -> cartService.addCartItem(null, PRODUCT_A, 1));

        verifyNoInteractions(productClient, transactionalService);
    }

    @Test
    void validateProducts_requestedThreeReturnedTwo_shouldThrowProductNotFoundException() {
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenReturn(List.of(activeProduct(PRODUCT_A), activeProduct(PRODUCT_C)));

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> cartService.validateProducts(List.of(PRODUCT_A, PRODUCT_B, PRODUCT_C))
        );

        assertTrue(exception.getMessage().contains(PRODUCT_B.toString()));
        verify(transactionalService, never()).addCartItem(any(), any(), anyInt(), any());
    }

    @Test
    void validateProducts_allRequestedProductsActive_shouldSucceed() {
        when(productClient.getProductsByIds(any(ProductLookupRequest.class)))
                .thenReturn(List.of(activeProduct(PRODUCT_A), activeProduct(PRODUCT_B), activeProduct(PRODUCT_C)));
        ArgumentCaptor<ProductLookupRequest> requestCaptor = ArgumentCaptor.forClass(ProductLookupRequest.class);

        cartService.validateProducts(List.of(PRODUCT_A, PRODUCT_B, PRODUCT_C));

        verify(productClient).getProductsByIds(requestCaptor.capture());
        assertEquals(List.of(PRODUCT_A, PRODUCT_B, PRODUCT_C), requestCaptor.getValue().requestedProductIds());
    }

    @Test
    void removeCartItem_existingItem_shouldLoadAndMutateCart() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 1, CREATED_AT);
        cart.addCartItem(PRODUCT_B, 1, CREATED_AT);
        when(cartRepository.findWithCartItemsById(CART_ID)).thenReturn(Optional.of(cart));

        cartService.removeCartItem(CART_ID, PRODUCT_A);

        assertEquals(1, cart.getCartItems().size());
        assertEquals(PRODUCT_B, cart.getCartItems().getFirst().getProductId());
        verify(cartRepository).findWithCartItemsById(CART_ID);
    }

    @Test
    void removeCartItem_missingCart_shouldThrowCartNotFoundException() {
        when(cartRepository.findWithCartItemsById(CART_ID)).thenReturn(Optional.empty());

        assertThrows(CartNotFoundException.class, () -> cartService.removeCartItem(CART_ID, PRODUCT_A));
    }

    @Test
    void removeCartItem_missingItem_shouldThrowCartItemNotFoundException() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 1, CREATED_AT);
        when(cartRepository.findWithCartItemsById(CART_ID)).thenReturn(Optional.of(cart));

        assertThrows(CartItemNotFoundException.class, () -> cartService.removeCartItem(CART_ID, PRODUCT_B));
        assertEquals(1, cart.getCartItems().size());
    }

    @Test
    void removeCartItem_invalidInput_shouldThrowWithoutLoadingCart() {
        assertThrows(IllegalArgumentException.class, () -> cartService.removeCartItem(null, PRODUCT_A));
        assertThrows(IllegalArgumentException.class, () -> cartService.removeCartItem(CART_ID, null));

        verifyNoInteractions(cartRepository);
    }

    @Test
    void changeCartItemQuantity_absoluteQuantity_shouldSetQuantity() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 2, CREATED_AT);
        when(cartRepository.findWithCartItemsById(CART_ID)).thenReturn(Optional.of(cart));

        Cart result = cartService.changeCartItemQuantity(CART_ID, PRODUCT_A, 5);

        assertSame(cart, result);
        assertEquals(5, result.getCartItems().getFirst().getQuantity());
    }

    @Test
    void changeCartItemQuantity_nonPositiveQuantity_shouldRejectWithoutLoadingCart() {
        assertThrows(
                IllegalArgumentException.class,
                () -> cartService.changeCartItemQuantity(CART_ID, PRODUCT_A, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> cartService.changeCartItemQuantity(CART_ID, PRODUCT_A, -1)
        );

        verifyNoInteractions(cartRepository);
    }

    @Test
    void changeCartItemQuantity_missingCart_shouldThrowCartNotFoundException() {
        when(cartRepository.findWithCartItemsById(CART_ID)).thenReturn(Optional.empty());

        assertThrows(
                CartNotFoundException.class,
                () -> cartService.changeCartItemQuantity(CART_ID, PRODUCT_A, 1)
        );
    }

    @Test
    void changeCartItemQuantity_missingItem_shouldThrowCartItemNotFoundException() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 1, CREATED_AT);
        when(cartRepository.findWithCartItemsById(CART_ID)).thenReturn(Optional.of(cart));

        assertThrows(
                CartItemNotFoundException.class,
                () -> cartService.changeCartItemQuantity(CART_ID, PRODUCT_B, 1)
        );
        assertEquals(1, cart.getCartItems().getFirst().getQuantity());
    }

    @Test
    void changeCartItemQuantity_nullProductId_shouldThrowWithoutLoadingCart() {
        assertThrows(
                IllegalArgumentException.class,
                () -> cartService.changeCartItemQuantity(CART_ID, null, 1)
        );

        verifyNoInteractions(cartRepository);
    }

    private static Cart checkoutLockedCart() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 1, CREATED_AT);
        cart.lockForCheckout(CREATED_AT.plusSeconds(1));
        return cart;
    }

    private static ProductLookupResponse activeProduct(UUID productId) {
        return product(productId, ProductStatus.ACTIVE);
    }

    private static ProductLookupResponse product(UUID productId, ProductStatus status) {
        return new ProductLookupResponse(productId, "Product", new BigDecimal("10.00"), status);
    }
}
