package cz.vsb.reservation.domain.exception;

/** BR-05: operace nad cizí rezervací. Mapuje se na HTTP 403. */
public class UnauthorizedReservationException extends RuntimeException {
    public UnauthorizedReservationException(String message) {
        super(message);
    }
}