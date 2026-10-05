package cz.vsb.reservation.infrastructure.web;

import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.port.in.ReservationUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reservations")
class ReservationController {

    private final ReservationUseCase reservationUseCase;

    ReservationController(ReservationUseCase reservationUseCase) {
        this.reservationUseCase = reservationUseCase;
    }

    @PostMapping
    ResponseEntity<ReservationResponse> create(
            @RequestHeader(value = RequestIdentity.USER_HEADER, required = false) String authenticatedUserId,
            @Valid @RequestBody CreateReservationRequest request) {

        String requester = RequestIdentity.require(authenticatedUserId);

        Reservation draft = reservationUseCase.createReservation(
                request.resourceId(),
                requester,             // kdo žádá
                request.userId(),      // pro koho (BR-05: musí být stejný)
                request.start(),
                request.end(),
                request.participantCount());

        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.from(draft));
    }

    /** OP-03: potvrzení provádí systém podle dostupnosti a pravidel, speciální učebny přecházejí do PENDING_APPROVAL (v0.2). */
    @PostMapping("/{id}/confirm")
    ReservationResponse confirm(
            @RequestHeader(value = RequestIdentity.USER_HEADER, required = false) String authenticatedUserId,
            @PathVariable Long id) {

        String requester = RequestIdentity.require(authenticatedUserId);
        return ReservationResponse.from(reservationUseCase.confirmReservation(id, requester));
    }

    @PostMapping("/{id}/approve")
    ReservationResponse approve(@RequestHeader(value = RequestIdentity.USER_HEADER, required = false) String user,
                                @PathVariable Long id) {
        return ReservationResponse.from(reservationUseCase.approveReservation(id, RequestIdentity.require(user)));
    }

    @PostMapping("/{id}/reject")
    ReservationResponse reject(@RequestHeader(value = RequestIdentity.USER_HEADER, required = false) String user,
                               @PathVariable Long id) {
        return ReservationResponse.from(reservationUseCase.rejectReservation(id, RequestIdentity.require(user)));
    }

    @GetMapping("/pending-approvals")
    List<ReservationDetailResponse> pending(@RequestHeader(value = RequestIdentity.USER_HEADER, required = false) String user) {
        return reservationUseCase.listPendingApprovals(RequestIdentity.require(user)).stream()
                .map(ReservationDetailResponse::from).toList();
    }

    /** OP-04: záznam zůstává, mění se jen stav na CANCELLED. */
    @PostMapping("/{id}/cancel")
    ReservationResponse cancel(
            @RequestHeader(value = RequestIdentity.USER_HEADER, required = false) String authenticatedUserId,
            @PathVariable Long id) {

        String requester = RequestIdentity.require(authenticatedUserId);
        return ReservationResponse.from(reservationUseCase.cancelReservation(id, requester));
    }

    /** Rozšíření mimo specifikaci v0.1: jen čtení, vrací pouze rezervace přihlášeného uživatele. */
    @GetMapping
    List<ReservationDetailResponse> listMine(
            @RequestHeader(value = RequestIdentity.USER_HEADER, required = false) String authenticatedUserId) {

        String requester = RequestIdentity.require(authenticatedUserId);
        return reservationUseCase.listReservations(requester).stream()
                .map(ReservationDetailResponse::from)
                .toList();
    }
}