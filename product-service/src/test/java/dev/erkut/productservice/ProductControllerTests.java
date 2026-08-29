package dev.erkut.productservice;

import dev.erkut.productservice.controller.ProductController;
import dev.erkut.productservice.dto.ProductCreateRequest;
import dev.erkut.productservice.dto.ProductResponse;
import dev.erkut.productservice.dto.ProductUpdateRequest;
import dev.erkut.productservice.exception.GlobalExceptionHandler;
import dev.erkut.productservice.exception.InvalidProductStateException;
import dev.erkut.productservice.exception.ProductNotFoundException;
import dev.erkut.productservice.model.ProductStatus;
import dev.erkut.productservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTests {

    private static final UUID PRODUCT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_PRODUCT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void createProductValidRequestReturnsCreated() throws Exception {
        when(productService.createProduct(any(ProductCreateRequest.class)))
                .thenReturn(productResponse(ProductStatus.ACTIVE));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":99.90}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(productService).createProduct(new ProductCreateRequest("Keyboard", new BigDecimal("99.90")));
    }

    @Test
    void createProductInvalidNameReturnsBadRequestWithErrorContract() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":99.90}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));
        verify(productService, never()).createProduct(any());
    }

    @Test
    void createProductInvalidPriceReturnsBadRequestWithErrorContract() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));

        verify(productService, never()).createProduct(any());
    }

    @Test
    void createProductPriceOverIntegerDigitsReturnsBadRequestWithErrorContract() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":123456789012345678.90}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));
    }

    @Test
    void createProductPriceOverScaleReturnsBadRequestWithErrorContract() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":10.001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));
    }

    @Test
    void getProductSuccessReturnsOkAndMissingReturnsNotFound() throws Exception {
        when(productService.getProductWithId(PRODUCT_ID)).thenReturn(productResponse(ProductStatus.ACTIVE));
        when(productService.getProductWithId(OTHER_PRODUCT_ID))
                .thenThrow(new ProductNotFoundException("product missing"));

        mockMvc.perform(get("/products/{productId}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keyboard"));
        mockMvc.perform(get("/products/{productId}", OTHER_PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("product missing"));
    }

    @Test
    void getProductsValidPaginationReturnsOk() throws Exception {
        when(productService.getProducts(1, 2))
                .thenReturn(new PageImpl<>(List.of(productResponse(ProductStatus.ACTIVE))));

        mockMvc.perform(get("/products").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(productService).getProducts(1, 2);
    }

    @Test
    void getProductsNegativePageReturnsBadRequestWithErrorContract() throws Exception {
        mockMvc.perform(get("/products").param("page", "-1").param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));

        verify(productService, never()).getProducts(any(Integer.class), any(Integer.class));
    }

    @Test
    void getProductsZeroSizeReturnsBadRequestWithErrorContract() throws Exception {
        mockMvc.perform(get("/products").param("page", "0").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));
        verify(productService, never()).getProducts(any(Integer.class), any(Integer.class));
    }

    @Test
    void getProductsOversizedPageReturnsBadRequestWithErrorContract() throws Exception {
        mockMvc.perform(get("/products").param("page", "0").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));
        verify(productService, never()).getProducts(any(Integer.class), any(Integer.class));
    }

    @Test
    void patchProductSupportsNameOnlyPriceOnlyAndBothFields() throws Exception {
        when(productService.updateProduct(eq(PRODUCT_ID), any(ProductUpdateRequest.class)))
                .thenReturn(productResponse(ProductStatus.ACTIVE));

        mockMvc.perform(patch("/products/{productId}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mechanical Keyboard\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/products/{productId}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":109.95}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/products/{productId}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mechanical Keyboard\",\"price\":109.95}"))
                .andExpect(status().isOk());

        verify(productService).updateProduct(PRODUCT_ID,
                new ProductUpdateRequest("Mechanical Keyboard", null));
        verify(productService).updateProduct(PRODUCT_ID,
                new ProductUpdateRequest(null, new BigDecimal("109.95")));
        verify(productService).updateProduct(PRODUCT_ID,
                new ProductUpdateRequest("Mechanical Keyboard", new BigDecimal("109.95")));
    }

    @Test
    void patchProductAllowsNullableFieldsButRejectsInvalidPrice() throws Exception {
        when(productService.updateProduct(eq(PRODUCT_ID), any(ProductUpdateRequest.class)))
                .thenReturn(productResponse(ProductStatus.ACTIVE));

        mockMvc.perform(patch("/products/{productId}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/products/{productId}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));
    }

    @Test
    void patchProductNameOverMaxLengthReturnsBadRequestWithErrorContract() throws Exception {
        mockMvc.perform(patch("/products/{productId}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request validation failed"));
        verify(productService, never()).updateProduct(any(), any());
    }

    @Test
    void patchProductInactiveExceptionReturnsConflict() throws Exception {
        when(productService.updateProduct(eq(PRODUCT_ID), any(ProductUpdateRequest.class)))
                .thenThrow(new InvalidProductStateException("inactive product"));

        mockMvc.perform(patch("/products/{productId}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New name\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("inactive product"));
    }

    @Test
    void deactivateProductSuccessReturnsOk() throws Exception {
        when(productService.deactivateProduct(PRODUCT_ID)).thenReturn(productResponse(ProductStatus.INACTIVE));

        mockMvc.perform(post("/products/{productId}/deactivate", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deleteProductSuccessReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/products/{productId}", PRODUCT_ID))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(PRODUCT_ID);
    }

    private static ProductResponse productResponse(ProductStatus status) {
        return new ProductResponse(PRODUCT_ID, "Keyboard", new BigDecimal("99.90"), status,
                CREATED_AT, CREATED_AT);
    }
}
