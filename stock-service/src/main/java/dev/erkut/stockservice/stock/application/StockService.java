package dev.erkut.stockservice.stock.application;

import dev.erkut.stockservice.inbox.application.InboxService;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.event.ProductCreatedEvent;
import dev.erkut.stockservice.stock.domain.StockItem;
import dev.erkut.stockservice.stock.persistence.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class StockService {

    private final StockItemRepository itemRepository;
    private final InboxService inboxService;
    public StockService(StockItemRepository itemRepository, InboxService inboxService) {
        this.itemRepository = itemRepository;
        this.inboxService = inboxService;
    }

    @Transactional
    public void handleProductCreated(MessageEnvelope envelope, ProductCreatedEvent event) {
        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        if (event == null) {
            throw new IllegalArgumentException("Product created event cannot be null");
        }
        if (event.productId() == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        Instant now = Instant.now();

        boolean registered = inboxService.tryRegister(
                envelope.messageId(),
                envelope.messageType(),
                event.productId(),
                now
        );

        if(!registered) {
            return;
        }

        StockItem item = StockItem.create(event.productId(), now);
        itemRepository.save(item);
    }
}
