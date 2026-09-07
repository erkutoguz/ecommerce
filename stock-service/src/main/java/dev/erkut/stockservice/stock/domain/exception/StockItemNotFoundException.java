package dev.erkut.stockservice.stock.domain.exception;

public class StockItemNotFoundException extends RuntimeException {
    public StockItemNotFoundException(String message) {
        super(message);
    }
}
