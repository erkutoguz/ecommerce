package dev.erkut.searchservice.application;

import dev.erkut.searchservice.document.ProductSearchDocument;
import dev.erkut.searchservice.document.ProductStatus;
import dev.erkut.searchservice.message.event.ProductCreatedEvent;
import dev.erkut.searchservice.message.event.ProductDeactivatedEvent;
import dev.erkut.searchservice.message.event.ProductDeletedEvent;
import dev.erkut.searchservice.message.event.ProductUpdatedEvent;
import dev.erkut.searchservice.persistence.ProductSearchDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductIndexServiceTest {
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(60);

    @Mock
    private ProductSearchDocumentRepository repository;

    @Test
    void createdUpdatedAndDeactivatedSnapshotsUpsertSameProductIdentity() {
        ProductIndexService service = new ProductIndexService(repository);
        service.handleCreated(new ProductCreatedEvent(
                PRODUCT_ID, "Keyboard", new BigDecimal("99.90"), ProductStatus.ACTIVE,
                CREATED_AT, CREATED_AT));
        service.handleUpdated(new ProductUpdatedEvent(
                PRODUCT_ID, "Mechanical Keyboard", new BigDecimal("109.90"), ProductStatus.ACTIVE,
                CREATED_AT, UPDATED_AT));
        service.handleDeactivated(new ProductDeactivatedEvent(
                PRODUCT_ID, "Mechanical Keyboard", new BigDecimal("109.90"), ProductStatus.INACTIVE,
                CREATED_AT, UPDATED_AT));

        ArgumentCaptor<ProductSearchDocument> captor = ArgumentCaptor.forClass(ProductSearchDocument.class);
        verify(repository, times(3)).save(captor.capture());
        assertEquals(PRODUCT_ID, captor.getAllValues().get(0).getProductId());
        assertEquals(PRODUCT_ID, captor.getAllValues().get(1).getProductId());
        ProductSearchDocument deactivated = captor.getAllValues().get(2);
        assertEquals("Mechanical Keyboard", deactivated.getName());
        assertEquals(new BigDecimal("109.90"), deactivated.getPrice());
        assertEquals(ProductStatus.INACTIVE, deactivated.getStatus());
        assertEquals(CREATED_AT, deactivated.getCreatedAt());
        assertEquals(UPDATED_AT, deactivated.getUpdatedAt());
    }

    @Test
    void deletedEventUsesProductIdAndRepeatedDeleteIsSafeAtRepositoryBoundary() {
        ProductIndexService service = new ProductIndexService(repository);
        ProductDeletedEvent event = new ProductDeletedEvent(PRODUCT_ID);

        service.handleDeleted(event);
        service.handleDeleted(event);

        verify(repository, times(2)).deleteById(PRODUCT_ID);
    }

    @Test
    void indexingFailurePropagatesToKafkaListenerForRetry() {
        ProductIndexService service = new ProductIndexService(repository);
        doThrow(new RuntimeException("Elasticsearch unavailable"))
                .when(repository).save(any(ProductSearchDocument.class));

        assertThrows(RuntimeException.class, () -> service.handleCreated(new ProductCreatedEvent(
                PRODUCT_ID, "Keyboard", new BigDecimal("99.90"), ProductStatus.ACTIVE,
                CREATED_AT, CREATED_AT)));
    }
}
