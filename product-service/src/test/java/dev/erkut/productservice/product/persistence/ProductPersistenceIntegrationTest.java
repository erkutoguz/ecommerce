package dev.erkut.productservice.product.persistence;

import dev.erkut.productservice.product.api.request.ProductCreateRequest;
import dev.erkut.productservice.product.api.request.ProductUpdateRequest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
@Testcontainers
class ProductPersistenceIntegrationTest {

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
        var created = productService.createProduct(
                new ProductCreateRequest("Original Product", new BigDecimal("25.50")));
        productService.updateProduct(created.productId(),
                new ProductUpdateRequest("Updated Product", new BigDecimal("30.00")));
        productRepository.flush();
        entityManager.clear();

        var reloaded = productRepository.findById(created.productId()).orElseThrow();
        assertEquals("Updated Product", reloaded.getName());
        assertEquals(new BigDecimal("30.00"), reloaded.getPrice());
        assertEquals(created.createdAt(), reloaded.getCreatedAt());
    }

    @Test
    void managedEntityDeactivationPersistsThroughDirtyChecking() {
        var created = productService.createProduct(
                new ProductCreateRequest("Active Product", new BigDecimal("25.50")));
        var deactivated = productService.deactivateProduct(created.productId());
        productRepository.flush();
        entityManager.clear();

        var reloaded = productRepository.findById(created.productId()).orElseThrow();
        assertEquals(ProductStatus.INACTIVE, reloaded.getStatus());
        assertEquals(deactivated.updatedAt(), reloaded.getUpdatedAt());
    }
}
