package dev.erkut.orderservice.cart.api;

import dev.erkut.orderservice.cart.api.error.CartExceptionHandler;
import dev.erkut.orderservice.cart.application.CartService;
import dev.erkut.orderservice.cart.application.exception.CartNotFoundException;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.exception.CartItemNotFoundException;
import dev.erkut.orderservice.integration.customer.InvalidCustomerStateException;
import dev.erkut.orderservice.integration.product.InvalidProductStateException;
import dev.erkut.orderservice.integration.product.ProductNotFoundException;
import dev.erkut.orderservice.api.error.GlobalExceptionHandler;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import({CartExceptionHandler.class, GlobalExceptionHandler.class})
class CartControllerTest {

    private static final UUID CART_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Test
    void getCart_existingCart_shouldReturnMappedCart() throws Exception {
        when(cartService.getCartById(CART_ID)).thenReturn(cartWithItem());

        mockMvc.perform(get("/carts/{cartId}", CART_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CART_ID.toString()))
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cartItems[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.cartItems[0].quantity").value(2));
        verify(cartService).getCartById(CART_ID);
    }

    @Test
    void getCart_missingCart_shouldReturnNotFound() throws Exception {
        when(cartService.getCartById(CART_ID))
                .thenThrow(new CartNotFoundException("Cart not found with id: " + CART_ID));

        mockMvc.perform(get("/carts/{cartId}", CART_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Cart not found with id: " + CART_ID));
    }

    @Test
    void getOpenCartByCustomerId_openCart_shouldReturnMappedCart() throws Exception {
        when(cartService.getOpenCartByCustomerId(CUSTOMER_ID)).thenReturn(cartWithItem());

        mockMvc.perform(get("/carts/current").param("customerId", CUSTOMER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CART_ID.toString()))
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(cartService).getOpenCartByCustomerId(CUSTOMER_ID);
    }

    @Test
    void addCartItem_validRequest_shouldReturnMappedCart() throws Exception {
        when(cartService.addCartItem(CART_ID, PRODUCT_ID, 2)).thenReturn(cartWithItem());

        mockMvc.perform(post("/carts/{cartId}/items", CART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":2}
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItems[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.cartItems[0].quantity").value(2));
        verify(cartService).addCartItem(CART_ID, PRODUCT_ID, 2);
    }

    @Test
    void addCartItem_nullProductId_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/carts/{cartId}/items", CART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":null,"quantity":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));
        verify(cartService, never()).addCartItem(any(), any(), anyInt());
    }

    @Test
    void addCartItem_nonPositiveQuantity_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/carts/{cartId}/items", CART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":0}
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));
        verify(cartService, never()).addCartItem(any(), any(), anyInt());
    }

    @Test
    void changeCartItemQuantity_validAbsoluteQuantity_shouldReturnMappedCart() throws Exception {
        Cart cart = cartWithItem();
        cart.changeCartItemQuantity(PRODUCT_ID, 5, CREATED_AT.plusSeconds(1));
        when(cartService.changeCartItemQuantity(CART_ID, PRODUCT_ID, 5)).thenReturn(cart);

        mockMvc.perform(patch("/carts/{cartId}/items/{productId}", CART_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItems[0].quantity").value(5));
        verify(cartService).changeCartItemQuantity(CART_ID, PRODUCT_ID, 5);
    }

    @Test
    void changeCartItemQuantity_nonPositiveQuantity_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(patch("/carts/{cartId}/items/{productId}", CART_ID, PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));
        verify(cartService, never()).changeCartItemQuantity(any(), any(), anyInt());
    }

    @Test
    void removeCartItem_success_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/carts/{cartId}/items/{productId}", CART_ID, PRODUCT_ID))
                .andExpect(status().isNoContent());
        verify(cartService).removeCartItem(CART_ID, PRODUCT_ID);
    }

    @Test
    void addCartItem_cartNotFound_shouldReturnNotFound() throws Exception {
        when(cartService.addCartItem(CART_ID, PRODUCT_ID, 2))
                .thenThrow(new CartNotFoundException("Cart not found with id: " + CART_ID));

        mockMvc.perform(post("/carts/{cartId}/items", CART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":2}
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Cart not found with id: " + CART_ID));
    }

    @Test
    void removeCartItem_missingItem_shouldReturnNotFound() throws Exception {
        doThrow(new CartItemNotFoundException("Cart item not found with product id: " + PRODUCT_ID))
                .when(cartService).removeCartItem(CART_ID, PRODUCT_ID);

        mockMvc.perform(delete("/carts/{cartId}/items/{productId}", CART_ID, PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Cart item not found with product id: " + PRODUCT_ID));
    }

    @Test
    void addCartItem_productNotFound_shouldReturnNotFound() throws Exception {
        when(cartService.addCartItem(eq(CART_ID), eq(PRODUCT_ID), eq(2)))
                .thenThrow(new ProductNotFoundException("Product not found with id(s): [" + PRODUCT_ID + "]"));

        mockMvc.perform(post("/carts/{cartId}/items", CART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":2}
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Product not found with id(s): [" + PRODUCT_ID + "]"));
    }

    @Test
    void addCartItem_inactiveProduct_shouldReturnConflict() throws Exception {
        when(cartService.addCartItem(CART_ID, PRODUCT_ID, 2))
                .thenThrow(new InvalidProductStateException("Product is not active"));

        mockMvc.perform(post("/carts/{cartId}/items", CART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":2}
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Product is not active"));
    }

    @Test
    void addCartItem_lockedCart_shouldReturnConflict() throws Exception {
        when(cartService.addCartItem(CART_ID, PRODUCT_ID, 2))
                .thenThrow(new IllegalStateException("Cart must be active"));

        mockMvc.perform(post("/carts/{cartId}/items", CART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":2}
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cart must be active"));
    }

    @Test
    void getOpenCartByCustomerId_inactiveCustomer_shouldReturnConflict() throws Exception {
        when(cartService.getOpenCartByCustomerId(CUSTOMER_ID))
                .thenThrow(new InvalidCustomerStateException("Customer is not active"));

        mockMvc.perform(get("/carts/current").param("customerId", CUSTOMER_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Customer is not active"));
    }

    private static Cart cartWithItem() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_ID, 2, CREATED_AT);
        setId(cart, CART_ID);
        return cart;
    }

    private static void setId(Cart cart, UUID id) {
        try {
            Field field = Cart.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(cart, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
