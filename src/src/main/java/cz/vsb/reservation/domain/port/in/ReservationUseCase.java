package cz.vsb.reservation.domain.port.in;

import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.model.Resource;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReservationUseCase {

    Reservation createReservation(Long resourceId, String requestingUserId, String ownerUserId,
                                  LocalDateTime start, LocalDateTime end, int participantCount);

    Reservation confirmReservation(Long reservationId, String requestingUserId);

    Reservation cancelReservation(Long reservationId, String requestingUserId);

    boolean checkAvailability(Long resourceId, String requestingUserId,
                              LocalDateTime start, LocalDateTime end);

    List<Resource> listResources(String requestingUserId);

    List<Reservation> listReservations(String requestingUserId);
}