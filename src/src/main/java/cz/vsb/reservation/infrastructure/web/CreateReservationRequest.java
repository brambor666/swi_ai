package cz.vsb.reservation.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Časy jsou v UTC (BR-01), převod do místního času dělá frontend. */
public record CreateReservationRequest(
        @NotNull Long resourceId,
        @NotBlank String userId,
        @NotNull LocalDateTime start,
        @NotNull LocalDateTime end,
        @NotNull Integer participantCount) {
}