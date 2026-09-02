package dev.erkut.productservice.product.application;

import dev.erkut.productservice.product.api.request.ProductCreateRequest;
import dev.erkut.productservice.product.api.response.ProductResponse;
import dev.erkut.productservice.product.api.request.ProductUpdateRequest;
import dev.erkut.productservice.product.domain.exception.InvalidProductStateException;
import dev.erkut.productservice.product.domain.exception.ProductNotFoundException;
import dev.erkut.productservice.product.domain.Product;
import dev.erkut.productservice.product.domain.ProductStatus;
import dev.erkut.productservice.product.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_PRODUCT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private ProductRepository productRepository;

    private ProductService productService() {
        return new ProductService(productRepository);
    }

    @Test
    void createProductCreatesSavesAndMapsProduct() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService().createProduct(
                new ProductCreateRequest("Keyboard", new BigDecimal("99.90")));

        assertEquals("Keyboard", response.name());
        assertEquals(new BigDecimal("99.90"), response.price());
        assertEquals(ProductStatus.ACTIVE, response.status());
        assertEquals(response.createdAt(), response.updatedAt());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void getProductWithIdReturnsMappedResponseOrThrowsWhenMissing() {
        Product product = product("Keyboard", new BigDecimal("99.90"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertEquals("Keyboard", productService().getProductWithId(PRODUCT_ID).name());

        when(productRepository.findById(OTHER_PRODUCT_ID)).thenReturn(Optional.empty());
        assertThrows(ProductNotFoundException.class,
                () -> productService().getProductWithId(OTHER_PRODUCT_ID));
    }

    @Test
    void getProductsMapsPageAndUsesExpectedPaginationAndSort() {
        Product first = product("Keyboard", new BigDecimal("99.90"));
        Product second = product("Mouse", new BigDecimal("49.90"));
        when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        var response = productService().getProducts(2, 25);

        assertEquals(2, response.getTotalElements());
        assertEquals("Keyboard", response.getContent().getFirst().name());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(25, pageable.getValue().getPageSize());
        assertEquals(List.of("createdAt", "id"),
                pageable.getValue().getSort().stream().map(order -> order.getProperty()).toList());
        assertTrue(pageable.getValue().getSort().stream().allMatch(order -> order.isDescending()));
    }

    @Test
    void updateProductWithNameOnlyChangesOnlyNameWithoutSavingManagedEntity() {
        Product product = product("Keyboard", new BigDecimal("99.90"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        ProductResponse response = productService().updateProduct(
                PRODUCT_ID, new ProductUpdateRequest("Mechanical Keyboard", null));

        assertEquals("Mechanical Keyboard", response.name());
        assertEquals(new BigDecimal("99.90"), response.price());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProductWithPriceOnlyChangesOnlyPriceWithoutSavingManagedEntity() {
        Product product = product("Keyboard", new BigDecimal("99.90"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        ProductResponse response = productService().updateProduct(
                PRODUCT_ID, new ProductUpdateRequest(null, new BigDecimal("109.95")));

        assertEquals("Keyboard", response.name());
        assertEquals(new BigDecimal("109.95"), response.price());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProductWithNameAndPriceChangesBothWithoutSavingManagedEntity() {
        Product product = product("Keyboard", new BigDecimal("99.90"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        ProductResponse response = productService().updateProduct(
                PRODUCT_ID, new ProductUpdateRequest("Mechanical Keyboard", new BigDecimal("109.95")));

        assertEquals("Mechanical Keyboard", response.name());
        assertEquals(new BigDecimal("109.95"), response.price());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProductWithBothFieldsNullUsesCurrentIllegalArgumentBehavior() {
        assertThrows(IllegalArgumentException.class,
                () -> productService().updateProduct(PRODUCT_ID, new ProductUpdateRequest(null, null)));

        verify(productRepository, never()).findById(any());
    }

    @Test
    void updateProductRejectsInactiveProduct() {
        Product product = product("Keyboard", new BigDecimal("99.90"));
        product.deactivateProduct(CREATED_AT.plusSeconds(1));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertThrows(InvalidProductStateException.class,
                () -> productService().updateProduct(
                        PRODUCT_ID, new ProductUpdateRequest("New name", null)));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deactivateProductMutatesManagedEntityWithoutSavingAgain() {
        Product product = product("Keyboard", new BigDecimal("99.90"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        ProductResponse response = productService().deactivateProduct(PRODUCT_ID);

        assertEquals(ProductStatus.INACTIVE, response.status());
        assertEquals(ProductStatus.INACTIVE, product.getStatus());
        assertTrue(response.updatedAt().isAfter(CREATED_AT));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deleteProductFindsAndDeletesExistingProduct() {
        Product product = product("Keyboard", new BigDecimal("99.90"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        productService().deleteProduct(PRODUCT_ID);

        verify(productRepository).delete(product);
    }

    @Test
    void deleteProductThrowsWhenProductIsMissing() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> productService().deleteProduct(PRODUCT_ID));
        verify(productRepository, never()).delete(any(Product.class));
    }

    private static Product product(String name, BigDecimal price) {
        return Product.create(name, price, CREATED_AT);
    }
}
