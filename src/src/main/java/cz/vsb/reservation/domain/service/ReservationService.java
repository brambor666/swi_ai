package cz.vsb.reservation.domain.service;

import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.model.Resource;
import cz.vsb.reservation.domain.port.in.ReservationUseCase;
import cz.vsb.reservation.domain.port.out.NotificationPort;
import cz.vsb.reservation.domain.port.out.ReservationRepository;
import cz.vsb.reservation.domain.port.out.ResourceRepository;

import java.time.LocalDateTime;
import java.util.List;

public class ReservationService implements ReservationUseCase {

    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final NotificationPort notificationPort;

    public ReservationService(ReservationRepository reservationRepository,
                              ResourceRepository resourceRepository,
                              NotificationPort notificationPort) {
        this.reservationRepository = reservationRepository;
        this.resourceRepository = resourceRepository;
        this.notificationPort = notificationPort;
    }

    @Override
    public Reservation createReservation(Long resourceId, String userId,
                                         LocalDateTime start, LocalDateTime end,
                                         int participantCount) {
        // Ověří, že učebna existuje - Resource entita si sama ověří kapacitu
        // až při potvrzení (confirm), tady jen chceme vědět, že resourceId je platné.
        resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Učebna s ID %d neexistuje".formatted(resourceId)));

        Reservation draft = Reservation.createDraft(resourceId, userId, start, end, participantCount);
        return reservationRepository.save(draft);
    }

    @Override
    public Reservation confirmReservation(Long reservationId) {
        Reservation reservation = getReservationOrThrow(reservationId);
        Resource resource = getResourceOrThrow(reservation.getResourceId());

        // ADR-002: ověření překryvu v doméně, PŘED spolehnutím na databázový
        // EXCLUDE constraint, který slouží jako poslední pojistka proti
        // souběhu dvou paralelních požadavků na stejný čas.
        if (hasOverlapWithConfirmedReservations(reservation)) {
            throw new IllegalStateException(
                    "Rezervace se překrývá s jinou potvrzenou rezervací stejné učebny");
        }

        reservation.confirm(resource);
        Reservation saved = reservationRepository.save(reservation);
        notificationPort.notifyConfirmed(saved);
        return saved;
    }

    @Override
    public Reservation cancelReservation(Long reservationId) {
        Reservation reservation = getReservationOrThrow(reservationId);

        reservation.cancel();
        Reservation saved = reservationRepository.save(reservation);
        notificationPort.notifyCancelled(saved);
        return saved;
    }

    @Override
    public boolean checkAvailability(Long resourceId, LocalDateTime start, LocalDateTime end) {
        List<Reservation> overlapping = reservationRepository
                .findConfirmedByResourceAndTimeRange(resourceId, start, end);
        return overlapping.isEmpty();
    }

    private boolean hasOverlapWithConfirmedReservations(Reservation reservation) {
        List<Reservation> existingConfirmed = reservationRepository
                .findConfirmedByResourceAndTimeRange(
                        reservation.getResourceId(),
                        reservation.getStartTime(),
                        reservation.getEndTime());

        // Vyloučíme sama sebe - pokud reservation už byla dřív uložená a
        // repository ji vrátí zpátky jako "existující", nejde o kolizi s jinou.
        return existingConfirmed.stream()
                .anyMatch(other -> !other.getId().equals(reservation.getId()));
    }

    private Reservation getReservationOrThrow(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rezervace s ID %d neexistuje".formatted(id)));
    }

    private Resource getResourceOrThrow(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Učebna s ID %d neexistuje".formatted(id)));
    }
}