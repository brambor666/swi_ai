package cz.vsb.reservation.domain;

import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.model.ReservationState;
import cz.vsb.reservation.domain.model.Resource;
import cz.vsb.reservation.domain.port.out.NotificationPort;
import cz.vsb.reservation.domain.port.out.ReservationRepository;
import cz.vsb.reservation.domain.port.out.ResourceRepository;
import cz.vsb.reservation.domain.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceTest {

    private final Resource resource = new Resource(1L, "Učebna A1", 20);

    private final Map<Long, Reservation> reservations = new HashMap<>();
    private long nextId = 1L;

    private ReservationRepository reservationRepository;
    private ResourceRepository resourceRepository;
    private NotificationPort notificationPort;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        reservations.clear();
        nextId = 1L;

        reservationRepository = new ReservationRepository() {
            @Override
            public Reservation save(Reservation reservation) {
                Long id = reservation.getId() != null ? reservation.getId() : nextId++;
                Reservation withId = new Reservation(id, reservation.getResourceId(),
                        reservation.getUserId(), reservation.getStartTime(),
                        reservation.getEndTime(), reservation.getParticipantCount(),
                        reservation.getState());
                reservations.put(id, withId);
                return withId;
            }

            @Override
            public Optional<Reservation> findById(Long id) {
                return Optional.ofNullable(reservations.get(id));
            }

            @Override
            public List<Reservation> findConfirmedByResourceAndTimeRange(
                    Long resourceId, LocalDateTime start, LocalDateTime end) {
                return reservations.values().stream()
                        .filter(r -> r.getState() == ReservationState.CONFIRMED)
                        .filter(r -> r.getResourceId().equals(resourceId))
                        .filter(r -> r.getStartTime().isBefore(end) && start.isBefore(r.getEndTime()))
                        .toList();
            }
        };

        resourceRepository = id -> Optional.of(resource).filter(r -> r.getId().equals(id));
        notificationPort = new NotificationPort() {
            @Override
            public void notifyConfirmed(Reservation reservation) {}
            @Override
            public void notifyCancelled(Reservation reservation) {}
        };

        service = new ReservationService(reservationRepository, resourceRepository, notificationPort);
    }

    @Test
    void confirmReservation_succeeds_whenNoOverlap() {
        Reservation created = service.createReservation(1L, "user-1",
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0), 10);

        Reservation confirmed = service.confirmReservation(created.getId());

        assertEquals(ReservationState.CONFIRMED, confirmed.getState());
    }

    @Test
    void confirmReservation_throws_whenOverlapsWithConfirmedReservation() {
        Reservation first = service.createReservation(1L, "user-1",
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0), 10);
        service.confirmReservation(first.getId());

        Reservation second = service.createReservation(1L, "user-2",
                LocalDateTime.of(2026, 10, 1, 10, 30),
                LocalDateTime.of(2026, 10, 1, 11, 30), 5);

        assertThrows(IllegalStateException.class, () -> service.confirmReservation(second.getId()));
    }

    @Test
    void confirmReservation_succeeds_whenReservationsAreBackToBack() {
        Reservation first = service.createReservation(1L, "user-1",
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0), 10);
        service.confirmReservation(first.getId());

        Reservation second = service.createReservation(1L, "user-2",
                LocalDateTime.of(2026, 10, 1, 11, 0),
                LocalDateTime.of(2026, 10, 1, 12, 0), 5);

        assertDoesNotThrow(() -> service.confirmReservation(second.getId()));
    }

    @Test
    void checkAvailability_returnsFalse_whenOverlappingConfirmedReservationExists() {
        Reservation reservation = service.createReservation(1L, "user-1",
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0), 10);
        service.confirmReservation(reservation.getId());

        boolean available = service.checkAvailability(1L,
                LocalDateTime.of(2026, 10, 1, 10, 30),
                LocalDateTime.of(2026, 10, 1, 10, 45));

        assertFalse(available);
    }
}