package dev.erkut.stockservice.reservation.application;

import dev.erkut.stockservice.inbox.application.InboxService;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ConfirmStockReservationCommand;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.message.event.StockReservationConfirmedEvent;
import dev.erkut.stockservice.message.event.StockReservationFailedEvent;
import dev.erkut.stockservice.message.event.StockReservedEvent;
import dev.erkut.stockservice.outbox.application.OutboxService;
import dev.erkut.stockservice.reservation.domain.Reservation;
import dev.erkut.stockservice.reservation.domain.StockReservationFailureReason;
import dev.erkut.stockservice.reservation.domain.exception.ReservationNotFoundException;
import dev.erkut.stockservice.reservation.persistence.ReservationRepository;
import dev.erkut.stockservice.stock.application.StockService;
import dev.erkut.stockservice.stock.domain.StockItem;
import dev.erkut.stockservice.stock.domain.exception.InactiveStockItemException;
import dev.erkut.stockservice.stock.domain.exception.InsufficientStockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final InboxService inboxService;
    private final StockService stockService;
    private final OutboxService outboxService;
    public ReservationService(
            ReservationRepository reservationRepository,
            InboxService inboxService,
            StockService stockService, OutboxService outboxService
    ) {
        this.reservationRepository = reservationRepository;
        this.inboxService = inboxService;
        this.stockService = stockService;
        this.outboxService = outboxService;
    }

    @Transactional
    public void handleReserveStock(MessageEnvelope envelope, ReserveStockCommand command) {
        validateCommand(command);

        Instant now = Instant.now();

        if (isDuplicate(envelope, command.orderId(), now)) {
            return;
        }

        List<StockItem> stockItems = new ArrayList<>();

        try {
            for (ReserveStockCommand.ReserveStockItem requestedItem : command.items()) {
                Optional<StockItem> stockItemOpt =
                        stockService.findStockItemById(requestedItem.productId());

                if (stockItemOpt.isEmpty()) {
                    StockReservationFailedEvent event = new StockReservationFailedEvent(
                            command.orderId(),
                            StockReservationFailureReason.ITEM_NOT_FOUND,
                            requestedItem.productId()
                    );
                    outboxService.createStockReservationFailedEvent(event, now);
                    return;
                }

                StockItem stockItem = stockItemOpt.get();
                stockItem.validateReservation(requestedItem.quantity());
                stockItems.add(stockItem);
            }
        } catch (InsufficientStockException ex) {
            StockReservationFailedEvent event = new StockReservationFailedEvent(
                    command.orderId(),
                    StockReservationFailureReason.INSUFFICIENT_STOCK,
                    ex.getProductId()
            );
            outboxService.createStockReservationFailedEvent(event, now);
            return;
        } catch (InactiveStockItemException ex) {
            StockReservationFailedEvent event = new StockReservationFailedEvent(
                    command.orderId(),
                    StockReservationFailureReason.ITEM_INACTIVE,
                    ex.getProductId()
            );
            outboxService.createStockReservationFailedEvent(event, now);
            return;
        }

        for (int i = 0; i < command.items().size(); i++) {
            ReserveStockCommand.ReserveStockItem requestedItem = command.items().get(i);
            StockItem stockItem = stockItems.get(i);
            stockItem.reserve(requestedItem.quantity());
        }

        Reservation reservation = Reservation.create(command.orderId(), now);

        for(ReserveStockCommand.ReserveStockItem reserveStockItem : command.items()) {
            reservation.addItem(reserveStockItem.productId(), reserveStockItem.quantity());
        }

        reservationRepository.save(reservation);
        StockReservedEvent event = new StockReservedEvent(command.orderId(), now);
        outboxService.createStockReservedEvent(event, now);
    }

    @Transactional
    public void handleConfirmStockReservationCommand(MessageEnvelope envelope, ConfirmStockReservationCommand command) {
        if(command == null) {
            throw new IllegalArgumentException("Confirm stock reservation command cannot be null");
        }

        if (command.orderId() == null) {
            throw new IllegalArgumentException("Order id cannot be null");
        }

        Instant now = Instant.now();

        if (isDuplicate(envelope, command.orderId(), now)) {
            return;
        }

        Reservation reservation = reservationRepository.findById(command.orderId())
                .orElseThrow(() ->
                        new ReservationNotFoundException("Reservation not found with id: " + command.orderId())
                );

        reservation.markConfirmed();
        StockReservationConfirmedEvent event = new StockReservationConfirmedEvent(command.orderId());
        outboxService.createStockReservationConfirmedEvent(event, now);
    }

    private boolean isDuplicate(
            MessageEnvelope envelope,
            UUID orderId,
            Instant now
    ) {
        return !inboxService.tryRegister(
                envelope.messageId(),
                envelope.messageType(),
                orderId,
                now
        );
    }
    private void validateCommand(ReserveStockCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Reserve stock command cannot be null");
        }
        if (command.orderId() == null) {
            throw new IllegalArgumentException("Order id cannot be null");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("Reserve stock items cannot be null or empty");
        }

        Set<UUID> productIds = new HashSet<>();
        for (ReserveStockCommand.ReserveStockItem item : command.items()) {
            if (item == null) {
                throw new IllegalArgumentException("Reserve stock item cannot be null");
            }
            if (item.productId() == null) {
                throw new IllegalArgumentException("Product id cannot be null");
            }
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero");
            }
            if (!productIds.add(item.productId())) {
                throw new IllegalArgumentException(
                        "Product already exists in reserve stock command: " + item.productId()
                );
            }
        }
    }
}
