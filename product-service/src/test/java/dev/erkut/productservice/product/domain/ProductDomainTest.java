package dev.erkut.productservice.product.domain;

import dev.erkut.productservice.product.domain.exception.InvalidProductStateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductDomainTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createWithValidValuesStartsActiveAndSetsBothTimestamps() {
        Product product = Product.create("Keyboard", new BigDecimal("99.90"), CREATED_AT);

        assertEquals("Keyboard", product.getName());
        assertEquals(new BigDecimal("99.90"), product.getPrice());
        assertEquals(ProductStatus.ACTIVE, product.getStatus());
        assertEquals(CREATED_AT, product.getCreatedAt());
        assertEquals(CREATED_AT, product.getUpdatedAt());
    }

    @Test
    void createRejectsBlankOrTooLongName() {
        assertThrows(IllegalArgumentException.class,
                () -> Product.create(null, new BigDecimal("10.00"), CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> Product.create("   ", new BigDecimal("10.00"), CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> Product.create("x".repeat(256), new BigDecimal("10.00"), CREATED_AT));
    }

    @Test
    void createRejectsNullZeroNegativeAndOverPrecisePrice() {
        assertThrows(IllegalArgumentException.class,
                () -> Product.create("Keyboard", null, CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> Product.create("Keyboard", BigDecimal.ZERO, CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> Product.create("Keyboard", new BigDecimal("-0.01"), CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> Product.create("Keyboard", new BigDecimal("10.001"), CREATED_AT));
    }

    @Test
    void changeProductNameOnActiveProductUpdatesNameAndTimestamp() {
        Product product = Product.create("Keyboard", new BigDecimal("99.90"), CREATED_AT);
        Instant updatedAt = CREATED_AT.plusSeconds(1);

        product.changeProductName("Mechanical Keyboard", updatedAt);

        assertEquals("Mechanical Keyboard", product.getName());
        assertEquals(updatedAt, product.getUpdatedAt());
    }

    @Test
    void changeProductNameRejectsInvalidNameAndInactiveProduct() {
        Product product = Product.create("Keyboard", new BigDecimal("99.90"), CREATED_AT);

        assertThrows(IllegalArgumentException.class,
                () -> product.changeProductName(" ", CREATED_AT.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> product.changeProductName("x".repeat(256), CREATED_AT.plusSeconds(1)));

        product.deactivateProduct(CREATED_AT.plusSeconds(1));
        assertThrows(InvalidProductStateException.class,
                () -> product.changeProductName("New name", CREATED_AT.plusSeconds(2)));
    }

    @Test
    void changeProductPriceOnActiveProductUpdatesPriceAndTimestamp() {
        Product product = Product.create("Keyboard", new BigDecimal("99.90"), CREATED_AT);
        Instant updatedAt = CREATED_AT.plusSeconds(1);

        product.changeProductPrice(new BigDecimal("109.95"), updatedAt);

        assertEquals(new BigDecimal("109.95"), product.getPrice());
        assertEquals(updatedAt, product.getUpdatedAt());
    }

    @Test
    void changeProductPriceRejectsInvalidPriceAndInactiveProduct() {
        Product product = Product.create("Keyboard", new BigDecimal("99.90"), CREATED_AT);

        assertThrows(IllegalArgumentException.class,
                () -> product.changeProductPrice(null, CREATED_AT.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> product.changeProductPrice(BigDecimal.ZERO, CREATED_AT.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> product.changeProductPrice(new BigDecimal("-1.00"), CREATED_AT.plusSeconds(1)));

        product.deactivateProduct(CREATED_AT.plusSeconds(1));
        assertThrows(InvalidProductStateException.class,
                () -> product.changeProductPrice(new BigDecimal("10.00"), CREATED_AT.plusSeconds(2)));
    }

    @Test
    void deactivateChangesActiveProductAndUpdatesTimestamp() {
        Product product = Product.create("Keyboard", new BigDecimal("99.90"), CREATED_AT);
        Instant deactivatedAt = CREATED_AT.plusSeconds(1);

        product.deactivateProduct(deactivatedAt);

        assertEquals(ProductStatus.INACTIVE, product.getStatus());
        assertEquals(deactivatedAt, product.getUpdatedAt());
    }

    @Test
    void deactivateIsIdempotentAndKeepsFirstTransitionTime() {
        Product product = Product.create("Keyboard", new BigDecimal("99.90"), CREATED_AT);
        Instant firstDeactivatedAt = CREATED_AT.plusSeconds(1);

        product.deactivateProduct(firstDeactivatedAt);
        product.deactivateProduct(CREATED_AT.plusSeconds(2));

        assertEquals(ProductStatus.INACTIVE, product.getStatus());
        assertEquals(firstDeactivatedAt, product.getUpdatedAt());
    }
}
