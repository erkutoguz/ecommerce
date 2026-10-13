package dev.erkut.searchservice.application;

import dev.erkut.searchservice.document.ProductStatus;
import dev.erkut.searchservice.message.event.ProductCreatedEvent;
import dev.erkut.searchservice.message.event.ProductDeletedEvent;
import dev.erkut.searchservice.message.event.ProductUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductIndexServiceTest {
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private ElasticsearchOperations operations;

    @Test
    void lifecycleEventsUseAtomicVersionedDocumentUpdates() {
        ProductIndexService service = new ProductIndexService(operations);
        service.handleCreated(new ProductCreatedEvent(PRODUCT_ID, "Keyboard", new BigDecimal("99.90"),
                ProductStatus.ACTIVE, CREATED_AT, CREATED_AT, 0));
        service.handleUpdated(new ProductUpdatedEvent(PRODUCT_ID, "Keyboard v2", new BigDecimal("109.90"),
                ProductStatus.ACTIVE, CREATED_AT, CREATED_AT.plusSeconds(1), 2));
        service.handleDeleted(new ProductDeletedEvent(PRODUCT_ID, 3));

        ArgumentCaptor<UpdateQuery> captor = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(operations, times(3)).update(captor.capture(), eq(IndexCoordinates.of("products")));
        UpdateQuery update = captor.getAllValues().get(1);
        assertTrue(update.getScript().contains("params.version > ctx._source.version"));
        assertEquals(2L, update.getParams().get("version"));
        Map<?, ?> document = (Map<?, ?>) update.getParams().get("document");
        assertEquals("Keyboard v2", document.get("name"));
        UpdateQuery delete = captor.getAllValues().get(2);
        Map<?, ?> tombstone = (Map<?, ?>) delete.getParams().get("document");
        assertEquals(true, tombstone.get("deleted"));
        assertEquals(3L, tombstone.get("version"));
    }

    @Test
    void indexingFailurePropagatesToKafkaListenerForRetry() {
        ProductIndexService service = new ProductIndexService(operations);
        doThrow(new RuntimeException("Elasticsearch unavailable"))
                .when(operations).update(any(UpdateQuery.class), any(IndexCoordinates.class));

        assertThrows(RuntimeException.class, () -> service.handleCreated(new ProductCreatedEvent(
                PRODUCT_ID, "Keyboard", new BigDecimal("99.90"), ProductStatus.ACTIVE,
                CREATED_AT, CREATED_AT, 1)));
    }
}
