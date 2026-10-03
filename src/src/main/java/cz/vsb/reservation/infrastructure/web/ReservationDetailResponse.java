package cz.vsb.reservation.infrastructure.web;

import cz.vsb.reservation.domain.model.Reservation;

import java.time.LocalDateTime;

/** Časy jsou v UTC (BR-01). */
public record ReservationDetailResponse(Long id, Long resourceId, String userId,
                                        LocalDateTime start, LocalDateTime end,
                                        int participantCount, String state) {

    static ReservationDetailResponse from(Reservation r) {
        return new ReservationDetailResponse(r.getId(), r.getResourceId(), r.getUserId(),
                r.getStartTime(), r.getEndTime(), r.getParticipantCount(), r.getState().name());
    }
}