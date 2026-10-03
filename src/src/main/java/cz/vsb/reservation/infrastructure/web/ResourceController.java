package cz.vsb.reservation.infrastructure.web;

import cz.vsb.reservation.domain.port.in.ReservationUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/resources")
class ResourceController {

    private final ReservationUseCase reservationUseCase;

    ResourceController(ReservationUseCase reservationUseCase) {
        this.reservationUseCase = reservationUseCase;
    }

    /** Rozšíření mimo specifikaci v0.1: jen čtení, potřebuje ho frontend. */
    @GetMapping
    List<ResourceResponse> list(
            @RequestHeader(value = RequestIdentity.USER_HEADER, required = false) String authenticatedUserId) {

        String requester = RequestIdentity.require(authenticatedUserId);
        return reservationUseCase.listResources(requester).stream()
                .map(ResourceResponse::from)
                .toList();
    }
}