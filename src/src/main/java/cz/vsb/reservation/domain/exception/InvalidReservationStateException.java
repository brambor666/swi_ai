package cz.vsb.reservation.domain.exception;

/** BR-04: nepovolený stavový přechod (např. potvrzení už CONFIRMED rezervace). Mapuje se na HTTP 409. */
public class InvalidReservationStateException extends RuntimeException {
    public InvalidReservationStateException(String message) {
        super(message);
    }
}