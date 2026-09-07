package dev.erkut.productservice.product.application;

import dev.erkut.productservice.outbox.application.OutboxService;
import dev.erkut.productservice.product.api.request.ProductCreateRequest;
import dev.erkut.productservice.product.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

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

    @Test
    void productCreationRollsBackWhenOutboxPersistenceFails() {
        doThrow(new RuntimeException("outbox failure"))
                .when(outboxService)
                .createProductCreatedEvent(any(), any());

        assertThrows(RuntimeException.class, () -> productService.createProduct(
                new ProductCreateRequest("Rollback Product", new BigDecimal("10.00"))));

        assertEquals(0, productRepository.count());
    }
}
