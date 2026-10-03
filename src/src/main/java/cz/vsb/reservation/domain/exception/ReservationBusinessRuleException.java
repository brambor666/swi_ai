package cz.vsb.reservation.domain.exception;

/** BR-03/BR-04/BR-02: nadkapacita, nesplněná 2h hranice, nebo konflikt překryvu. Mapuje se na HTTP 409. */
public class ReservationBusinessRuleException extends RuntimeException {
    public ReservationBusinessRuleException(String message) {
        super(message);
    }
}