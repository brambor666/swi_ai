package cz.vsb.reservation.infrastructure.web;

import cz.vsb.reservation.domain.port.in.ReservationUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/resources/{resourceId}/availability")
class AvailabilityController {

    private final ReservationUseCase reservationUseCase;

    AvailabilityController(ReservationUseCase reservationUseCase) {
        this.reservationUseCase = reservationUseCase;
    }

    /** OP-02: dostupnost může zjišťovat každý ověřený uživatel; dotaz nic nemění. Časy jsou v UTC. */
    @GetMapping
    AvailabilityResponse check(
            @RequestHeader(value = RequestIdentity.USER_HEADER, required = false) String authenticatedUserId,
            @PathVariable Long resourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        String requester = RequestIdentity.require(authenticatedUserId);
        return new AvailabilityResponse(
                reservationUseCase.checkAvailability(resourceId, requester, start, end));
    }
}