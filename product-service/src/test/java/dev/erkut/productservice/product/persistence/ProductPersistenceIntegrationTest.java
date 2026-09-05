package dev.erkut.productservice.product.persistence;

import dev.erkut.productservice.product.api.request.ProductCreateRequest;
import dev.erkut.productservice.product.domain.Product;
import dev.erkut.productservice.product.domain.ProductStatus;
import dev.erkut.productservice.product.application.ProductService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
@Testcontainers
class ProductPersistenceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-05T12:30:15.123456Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-05T12:30:16.654321Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void productPersistsWithGeneratedUuidAndCanBeReadBack() {
        var created = productService.createProduct(
                new ProductCreateRequest("Persistence Product", new BigDecimal("25.50")));
        productRepository.flush();
        entityManager.clear();

        assertNotNull(created.productId());
        var reloaded = productRepository.findById(created.productId()).orElseThrow();
        assertEquals(created.productId(), reloaded.getId());
        assertEquals("Persistence Product", reloaded.getName());
        assertEquals(new BigDecimal("25.50"), reloaded.getPrice());
        assertEquals(ProductStatus.ACTIVE, reloaded.getStatus());
    }

    @Test
    void managedEntityNameAndPriceUpdatesPersistThroughDirtyChecking() {
        var created = Product.create("Original Product", new BigDecimal("25.50"), CREATED_AT);
        productRepository.save(created);
        productRepository.flush();

        created.changeProductName("Updated Product", UPDATED_AT);
        created.changeProductPrice(new BigDecimal("30.00"), UPDATED_AT);
        productRepository.flush();
        entityManager.clear();

        var reloaded = productRepository.findById(created.getId()).orElseThrow();
        assertEquals("Updated Product", reloaded.getName());
        assertEquals(new BigDecimal("30.00"), reloaded.getPrice());
        assertEquals(CREATED_AT, reloaded.getCreatedAt());
    }

    @Test
    void managedEntityDeactivationPersistsThroughDirtyChecking() {
        var created = Product.create("Active Product", new BigDecimal("25.50"), CREATED_AT);
        productRepository.save(created);
        productRepository.flush();

        created.deactivateProduct(UPDATED_AT);
        productRepository.flush();
        entityManager.clear();

        var reloaded = productRepository.findById(created.getId()).orElseThrow();
        assertEquals(ProductStatus.INACTIVE, reloaded.getStatus());
        assertEquals(UPDATED_AT, reloaded.getUpdatedAt());
    }
}
