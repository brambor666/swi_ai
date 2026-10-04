package cz.vsb.reservation.domain.exception;

/** Neplatný vstup (prázdné ID, start >= end, neplatný počet účastníků...). Mapuje se na HTTP 400. */
public class ReservationValidationException extends RuntimeException {
    public ReservationValidationException(String message) {
        super(message);
    }
}