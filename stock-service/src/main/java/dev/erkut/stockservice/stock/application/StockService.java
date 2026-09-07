package dev.erkut.stockservice.stock.application;

import dev.erkut.stockservice.inbox.application.InboxService;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.event.ProductCreatedEvent;
import dev.erkut.stockservice.message.event.ProductDeactivatedEvent;
import dev.erkut.stockservice.stock.domain.StockItem;
import dev.erkut.stockservice.stock.domain.exception.StockItemNotFoundException;
import dev.erkut.stockservice.stock.persistence.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class StockService {

    private final StockItemRepository itemRepository;
    private final InboxService inboxService;
    public StockService(StockItemRepository itemRepository, InboxService inboxService) {
        this.itemRepository = itemRepository;
        this.inboxService = inboxService;
    }

    @Transactional
    public void handleProductCreated(
            MessageEnvelope envelope,
            ProductCreatedEvent event
    ) {
        if (event == null) {
            throw new IllegalArgumentException("Product created event cannot be null");
        }

        Instant now = Instant.now();

        if (isDuplicate(envelope, event.productId(), now)) {
            return;
        }

        StockItem item = StockItem.create(event.productId(), now);
        itemRepository.save(item);
    }

    @Transactional
    public void handleProductDeactivated(
            MessageEnvelope envelope,
            ProductDeactivatedEvent event
    ) {
        if (event == null) {
            throw new IllegalArgumentException("Product deactivated event cannot be null");
        }

        Instant now = Instant.now();

        if (isDuplicate(envelope, event.productId(), now)) {
            return;
        }

        StockItem item = itemRepository.findById(event.productId())
                .orElseThrow(() ->
                        new StockItemNotFoundException("Stock item not found with id: " + event.productId())
                );

        item.deactivate();
    }

    @Transactional(readOnly = true)
    public Optional<StockItem> findStockItemById(UUID productId) {
        return itemRepository.findById(productId);
    }

    private boolean isDuplicate(
            MessageEnvelope envelope,
            UUID productId,
            Instant processedAt
    ) {
        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }

        if (productId == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        return !inboxService.tryRegister(
                envelope.messageId(),
                envelope.messageType(),
                productId,
                processedAt
        );
    }
}
