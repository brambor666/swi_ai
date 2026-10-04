package cz.vsb.reservation.domain.service;

import cz.vsb.reservation.domain.exception.ReservationBusinessRuleException;
import cz.vsb.reservation.domain.exception.ReservationNotFoundException;
import cz.vsb.reservation.domain.exception.ReservationValidationException;
import cz.vsb.reservation.domain.exception.UnauthorizedReservationException;
import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.model.Resource;
import cz.vsb.reservation.domain.port.in.ReservationUseCase;
import cz.vsb.reservation.domain.port.out.NotificationPort;
import cz.vsb.reservation.domain.port.out.ReservationRepository;
import cz.vsb.reservation.domain.port.out.ResourceRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class ReservationService implements ReservationUseCase {

    // System.Logger je součást JDK, doména tedy pořád nezávisí na žádném frameworku.
    private static final System.Logger LOG = System.getLogger(ReservationService.class.getName());

    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final NotificationPort notificationPort;
    private final Clock clock;

    public ReservationService(ReservationRepository reservationRepository,
                              ResourceRepository resourceRepository,
                              NotificationPort notificationPort,
                              Clock clock) {
        this.reservationRepository = reservationRepository;
        this.resourceRepository = resourceRepository;
        this.notificationPort = notificationPort;
        this.clock = clock;
    }

    @Override
    public Reservation createReservation(Long resourceId, String requestingUserId, String ownerUserId,
                                         LocalDateTime start, LocalDateTime end, int participantCount) {
        Resource resource = getResourceOrThrow(resourceId);
        // Kolize s jinou rezervací vytvoření návrhu nebrání (REQ-02), proto tu žádná kontrola překryvu není.
        Reservation draft = Reservation.createDraft(
                resource, requestingUserId, ownerUserId, start, end, participantCount, now());
        return reservationRepository.save(draft);
    }

    @Override
    public Reservation confirmReservation(Long reservationId, String requestingUserId) {
        Reservation reservation = getReservationOrThrow(reservationId);
        Resource resource = getResourceOrThrow(reservation.getResourceId());

        // Vlastnictví, časová hranice, stavový přechod a kapacita (BR-03/04/05).
        // Změna je zatím jen v paměti, uloží se až po kontrole konfliktu.
        reservation.confirm(resource, requestingUserId, now());

        // BR-02: kontrola překryvu až po kontrole vlastnictví, aby cizí uživatel nezjistil nic o cizích rezervacích.
        // Databázový EXCLUDE constraint zůstává poslední pojistkou při souběhu.
        if (hasOverlapWithConfirmedReservations(reservation)) {
            throw new ReservationBusinessRuleException(
                    "Rezervace se překrývá s jinou potvrzenou rezervací stejné učebny");
        }

        Reservation saved = reservationRepository.save(reservation);
        notifySafely(() -> notificationPort.notifyConfirmed(saved));
        return saved;
    }

    @Override
    public Reservation cancelReservation(Long reservationId, String requestingUserId) {
        Reservation reservation = getReservationOrThrow(reservationId);

        reservation.cancel(requestingUserId, now());

        Reservation saved = reservationRepository.save(reservation);
        notifySafely(() -> notificationPort.notifyCancelled(saved));
        return saved;
    }

    @Override
    public boolean checkAvailability(Long resourceId, String requestingUserId,
                                     LocalDateTime start, LocalDateTime end) {
        if (requestingUserId == null || requestingUserId.isBlank()) {
            throw new UnauthorizedReservationException("Dostupnost může zjišťovat pouze ověřený uživatel");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ReservationValidationException("Interval musí být kompletní a začátek před koncem");
        }
        getResourceOrThrow(resourceId);

        // Dotaz nevyžaduje 2h lhůtu (BR-04) a nemění žádná data.
        return reservationRepository
                .findConfirmedByResourceAndTimeRange(resourceId, start, end)
                .isEmpty();
    }

    private boolean hasOverlapWithConfirmedReservations(Reservation reservation) {
        return reservationRepository
                .findConfirmedByResourceAndTimeRange(
                        reservation.getResourceId(),
                        reservation.getStartTime(),
                        reservation.getEndTime())
                .stream()
                .anyMatch(other -> !other.getId().equals(reservation.getId()));
    }

    /** BR-07: selhání notifikace se zahodí bez opakování, výsledek operace se nemění. */
    private void notifySafely(Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Oznámení se nepodařilo předat, zahazuji ho (BR-07)", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock); // clock je v UTC (BR-01)
    }

    private Reservation getReservationOrThrow(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Rezervace s ID %d neexistuje".formatted(id)));
    }

    private Resource getResourceOrThrow(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "Učebna s ID %d neexistuje".formatted(id)));
    }

    @Override
    public List<Resource> listResources(String requestingUserId) {
        requireAuthenticated(requestingUserId);
        return resourceRepository.findAll();
    }

    /** Jen vlastní rezervace (BR-05), včetně zrušených. */
    @Override
    public List<Reservation> listReservations(String requestingUserId) {
        requireAuthenticated(requestingUserId);
        return reservationRepository.findByUserId(requestingUserId);
    }

    private void requireAuthenticated(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedReservationException("Operaci může provést pouze ověřený uživatel");
        }
    }
}