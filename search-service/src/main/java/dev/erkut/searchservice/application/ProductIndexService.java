package dev.erkut.searchservice.application;

import dev.erkut.searchservice.document.ProductSearchDocument;
import dev.erkut.searchservice.document.ProductStatus;
import dev.erkut.searchservice.message.event.ProductCreatedEvent;
import dev.erkut.searchservice.message.event.ProductDeactivatedEvent;
import dev.erkut.searchservice.message.event.ProductDeletedEvent;
import dev.erkut.searchservice.message.event.ProductUpdatedEvent;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductIndexService {
    private static final Logger log = LoggerFactory.getLogger(ProductIndexService.class);

    private static final IndexCoordinates PRODUCTS_INDEX = IndexCoordinates.of("products");
    private static final String VERSIONED_UPSERT_SCRIPT = """
            if (ctx.op == 'create') {
                ctx._source.putAll(params.document);
            } else if (ctx._source.version == null) {
                if (params.version > 0 || params.document.deleted || params.document.status == 'INACTIVE') {
                    ctx._source.putAll(params.document);
                } else {
                    ctx.op = 'noop';
                }
            } else if (params.version > ctx._source.version) {
                ctx._source.putAll(params.document);
            } else {
                ctx.op = 'noop';
            }
            """;

    private final ElasticsearchOperations elasticsearchOperations;

    public ProductIndexService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public void handleCreated(ProductCreatedEvent event) {
        save(event.productId(), event.name(), event.price(), event.status(), event.createdAt(), event.updatedAt(), event.version());
    }

    public void handleUpdated(ProductUpdatedEvent event) {
        save(event.productId(), event.name(), event.price(), event.status(), event.createdAt(), event.updatedAt(), event.version());
    }

    public void handleDeactivated(ProductDeactivatedEvent event) {
        save(event.productId(), event.name(), event.price(), event.status(), event.createdAt(), event.updatedAt(), event.version());
    }

    public void handleDeleted(ProductDeletedEvent event) {
        ProductSearchDocument tombstone = ProductSearchDocument.tombstone(event.productId(), event.version());
        applyVersioned(tombstone);
        log.info("Applied Product deletion to search index: productId={}, version={}", event.productId(), event.version());
    }

    private void save(
            UUID productId,
            String name,
            BigDecimal price,
            ProductStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        ProductSearchDocument document = ProductSearchDocument.create(
                productId, name, price, status, createdAt, updatedAt, version);
        applyVersioned(document);
        log.info("Indexed Product: productId={}, status={}, version={}", productId, status, version);
    }

    private void applyVersioned(ProductSearchDocument document) {
        Map<String, Object> source = Map.of(
                "productId", document.getProductId().toString(),
                "name", document.getName(),
                "price", document.getPrice(),
                "status", document.getStatus().name(),
                "createdAt", document.getCreatedAt().toString(),
                "updatedAt", document.getUpdatedAt().toString(),
                "version", document.getVersion(),
                "deleted", document.isDeleted()
        );
        UpdateQuery update = UpdateQuery.builder(document.getProductId().toString())
                .withScript(VERSIONED_UPSERT_SCRIPT)
                .withParams(Map.of("version", document.getVersion(), "document", source))
                .withUpsert(Document.create())
                .withScriptedUpsert(true)
                .withRetryOnConflict(5)
                .build();
        elasticsearchOperations.update(update, PRODUCTS_INDEX);
    }
}
