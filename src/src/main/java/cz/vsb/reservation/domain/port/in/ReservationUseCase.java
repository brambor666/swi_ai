package cz.vsb.reservation.domain.port.in;

import cz.vsb.reservation.domain.model.Reservation;

import java.time.LocalDateTime;

public interface ReservationUseCase {

    Reservation createReservation(Long resourceId, String userId,
                                  LocalDateTime start, LocalDateTime end,
                                  int participantCount);

    Reservation confirmReservation(Long reservationId);

    Reservation cancelReservation(Long reservationId);

    boolean checkAvailability(Long resourceId, LocalDateTime start, LocalDateTime end);
}