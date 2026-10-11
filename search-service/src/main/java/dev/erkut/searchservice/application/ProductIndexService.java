package dev.erkut.searchservice.application;

import dev.erkut.searchservice.document.ProductSearchDocument;
import dev.erkut.searchservice.document.ProductStatus;
import dev.erkut.searchservice.message.event.ProductCreatedEvent;
import dev.erkut.searchservice.message.event.ProductDeactivatedEvent;
import dev.erkut.searchservice.message.event.ProductDeletedEvent;
import dev.erkut.searchservice.message.event.ProductUpdatedEvent;
import dev.erkut.searchservice.persistence.ProductSearchDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class ProductIndexService {
    private static final Logger log = LoggerFactory.getLogger(ProductIndexService.class);

    private final ProductSearchDocumentRepository productSearchDocumentRepository;

    public ProductIndexService(ProductSearchDocumentRepository productSearchDocumentRepository) {
        this.productSearchDocumentRepository = productSearchDocumentRepository;
    }

    public void handleCreated(ProductCreatedEvent event) {
        save(event.productId(), event.name(), event.price(), event.status(), event.createdAt(), event.updatedAt());
    }

    public void handleUpdated(ProductUpdatedEvent event) {
        save(event.productId(), event.name(), event.price(), event.status(), event.createdAt(), event.updatedAt());
    }

    public void handleDeactivated(ProductDeactivatedEvent event) {
        save(event.productId(), event.name(), event.price(), event.status(), event.createdAt(), event.updatedAt());
    }

    public void handleDeleted(ProductDeletedEvent event) {
        productSearchDocumentRepository.deleteById(event.productId());
        log.info("Removed Product from search index: productId={}", event.productId());
    }

    private void save(
            UUID productId,
            String name,
            BigDecimal price,
            ProductStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        ProductSearchDocument document = ProductSearchDocument.create(
                productId, name, price, status, createdAt, updatedAt);
        productSearchDocumentRepository.save(document);
        log.info("Indexed Product: productId={}, status={}", productId, status);
    }
}
