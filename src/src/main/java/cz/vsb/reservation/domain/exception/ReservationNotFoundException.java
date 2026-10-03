package cz.vsb.reservation.domain.exception;

/** Rezervace nebo učebna neexistuje. Mapuje se na HTTP 404. */
public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String message) {
        super(message);
    }
}