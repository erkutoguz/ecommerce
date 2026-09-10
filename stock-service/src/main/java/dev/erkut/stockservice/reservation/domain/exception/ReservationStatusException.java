package dev.erkut.stockservice.reservation.domain.exception;

public class ReservationStatusException extends RuntimeException {
    public ReservationStatusException(String message) {
        super(message);
    }
}
