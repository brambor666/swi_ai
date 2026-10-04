package cz.vsb.reservation.infrastructure.web;

import cz.vsb.reservation.domain.model.Reservation;

public record ReservationResponse(Long id, String state) {

    static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getState().name());
    }
}