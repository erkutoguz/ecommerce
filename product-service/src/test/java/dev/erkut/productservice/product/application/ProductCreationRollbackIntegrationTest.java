package dev.erkut.productservice.product.application;

import dev.erkut.productservice.outbox.application.OutboxService;
import dev.erkut.productservice.product.api.request.ProductCreateRequest;
import dev.erkut.productservice.product.api.request.ProductUpdateRequest;
import dev.erkut.productservice.product.domain.Product;
import dev.erkut.productservice.product.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class ProductCreationRollbackIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @MockitoBean
    private OutboxService outboxService;

    @BeforeEach
    void clearProducts() {
        productRepository.deleteAll();
        productRepository.flush();
    }

    @Test
    void productCreationRollsBackWhenOutboxPersistenceFails() {
        doThrow(new RuntimeException("outbox failure"))
                .when(outboxService)
                .createProductCreatedEvent(any(), any());

        assertThrows(RuntimeException.class, () -> productService.createProduct(
                new ProductCreateRequest("Rollback Product", new BigDecimal("10.00"))));

        assertEquals(0, productRepository.count());
    }

    @Test
    void productUpdateRollsBackWhenOutboxPersistenceFails() {
        Product product = productRepository.saveAndFlush(Product.create(
                "Original", new BigDecimal("10.00"), Instant.parse("2026-01-01T10:00:00Z")));
        doThrow(new RuntimeException("outbox failure"))
                .when(outboxService)
                .createProductUpdatedEvent(any(), any());

        assertThrows(RuntimeException.class, () -> productService.updateProduct(
                product.getId(), new ProductUpdateRequest("Changed", null)));

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertEquals("Original", reloaded.getName());
    }

    @Test
    void productDeleteRollsBackWhenOutboxPersistenceFails() {
        Product product = productRepository.saveAndFlush(Product.create(
                "Keep", new BigDecimal("10.00"), Instant.parse("2026-01-01T10:00:00Z")));
        doThrow(new RuntimeException("outbox failure"))
                .when(outboxService)
                .createProductDeletedEvent(any(), any());

        assertThrows(RuntimeException.class, () -> productService.deleteProduct(product.getId()));

        assertEquals(true, productRepository.findById(product.getId()).isPresent());
    }
}
