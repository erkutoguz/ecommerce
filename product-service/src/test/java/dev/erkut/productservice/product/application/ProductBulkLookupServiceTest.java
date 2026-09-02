package dev.erkut.productservice.product.application;

import dev.erkut.productservice.product.api.request.ProductBulkRequest;
import dev.erkut.productservice.product.api.response.ProductResponse;
import dev.erkut.productservice.product.domain.exception.ProductNotFoundException;
import dev.erkut.productservice.product.domain.Product;
import dev.erkut.productservice.product.domain.ProductStatus;
import dev.erkut.productservice.product.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductBulkLookupServiceTest {

    private static final UUID FIRST_PRODUCT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SECOND_PRODUCT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID MISSING_PRODUCT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private ProductRepository productRepository;

    private ProductService productService() {
        return new ProductService(productRepository);
    }

    @Test
    void bulkLookupFindsAllRequestedProductsAndMapsEveryResponse() throws Exception {
        Product first = product(FIRST_PRODUCT_ID, "Keyboard", ProductStatus.ACTIVE);
        Product second = product(SECOND_PRODUCT_ID, "Mouse", ProductStatus.ACTIVE);
        when(productRepository.findAllById(any())).thenReturn(List.of(second, first));

        List<ProductResponse> response = productService().bulkLookup(
                new ProductBulkRequest(List.of(FIRST_PRODUCT_ID, SECOND_PRODUCT_ID)));

        assertEquals(Set.of(FIRST_PRODUCT_ID, SECOND_PRODUCT_ID),
                response.stream().map(ProductResponse::productId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("Keyboard", "Mouse"),
                response.stream().map(ProductResponse::name).collect(java.util.stream.Collectors.toSet()));
        verify(productRepository).findAllById(argThat(ids ->
                Set.copyOf(toList(ids)).equals(Set.of(FIRST_PRODUCT_ID, SECOND_PRODUCT_ID))));
    }

    @Test
    void bulkLookupDeduplicatesRequestedIdsBeforeLookupAndResponse() throws Exception {
        Product first = product(FIRST_PRODUCT_ID, "Keyboard", ProductStatus.ACTIVE);
        when(productRepository.findAllById(any())).thenReturn(List.of(first));

        List<ProductResponse> response = productService().bulkLookup(
                new ProductBulkRequest(List.of(FIRST_PRODUCT_ID, FIRST_PRODUCT_ID)));

        assertEquals(1, response.size());
        assertEquals(FIRST_PRODUCT_ID, response.getFirst().productId());
        verify(productRepository).findAllById(argThat(ids ->
                Set.copyOf(toList(ids)).equals(Set.of(FIRST_PRODUCT_ID))));
    }

    @Test
    void bulkLookupThrowsWithMissingProductIdInMessage() throws Exception {
        Product first = product(FIRST_PRODUCT_ID, "Keyboard", ProductStatus.ACTIVE);
        when(productRepository.findAllById(any())).thenReturn(List.of(first));

        ProductNotFoundException exception = assertThrows(ProductNotFoundException.class,
                () -> productService().bulkLookup(new ProductBulkRequest(
                        List.of(FIRST_PRODUCT_ID, MISSING_PRODUCT_ID))));

        assertEquals(true, exception.getMessage().contains(MISSING_PRODUCT_ID.toString()));
    }

    @Test
    void bulkLookupIncludesAllMissingIdsWhenSeveralProductsAreAbsent() {
        UUID secondMissingId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        when(productRepository.findAllById(any())).thenReturn(List.of());

        ProductNotFoundException exception = assertThrows(ProductNotFoundException.class,
                () -> productService().bulkLookup(new ProductBulkRequest(
                        List.of(MISSING_PRODUCT_ID, secondMissingId))));

        assertEquals(true, exception.getMessage().contains(MISSING_PRODUCT_ID.toString()));
        assertEquals(true, exception.getMessage().contains(secondMissingId.toString()));
    }

    @Test
    void bulkLookupTreatsNoFoundProductsAsNotFound() {
        when(productRepository.findAllById(any())).thenReturn(List.of());

        assertThrows(ProductNotFoundException.class,
                () -> productService().bulkLookup(new ProductBulkRequest(List.of(FIRST_PRODUCT_ID))));
    }

    @Test
    void bulkLookupReturnsInactiveProductWithoutApplyingOrderBusinessRules() throws Exception {
        Product inactive = product(FIRST_PRODUCT_ID, "Discontinued Keyboard", ProductStatus.INACTIVE);
        when(productRepository.findAllById(any())).thenReturn(List.of(inactive));

        List<ProductResponse> response = productService().bulkLookup(
                new ProductBulkRequest(List.of(FIRST_PRODUCT_ID)));

        assertEquals(1, response.size());
        assertEquals(ProductStatus.INACTIVE, response.getFirst().status());
    }

    private static Product product(UUID id, String name, ProductStatus status) throws Exception {
        Product product = Product.create(name, new BigDecimal("99.90"), CREATED_AT);
        setField(product, "id", id);
        if (status == ProductStatus.INACTIVE) {
            product.deactivateProduct(CREATED_AT.plusSeconds(1));
        }
        return product;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static List<UUID> toList(Iterable<UUID> ids) {
        List<UUID> values = new java.util.ArrayList<>();
        ids.forEach(values::add);
        return values;
    }
}
