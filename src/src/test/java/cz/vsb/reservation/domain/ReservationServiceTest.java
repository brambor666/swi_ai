package cz.vsb.reservation.domain;

import cz.vsb.reservation.domain.exception.*;
import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.model.ReservationState;
import cz.vsb.reservation.domain.model.Resource;
import cz.vsb.reservation.domain.port.out.NotificationPort;
import cz.vsb.reservation.domain.port.out.ReservationRepository;
import cz.vsb.reservation.domain.port.out.ResourceRepository;
import cz.vsb.reservation.domain.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceTest {

    private static final String OWNER = "user-1";
    private static final String OTHER = "user-2";
    private static final LocalDateTime T10 = LocalDateTime.of(2026, 10, 1, 10, 0);

    /** Pevné, posunovatelné hodiny v UTC. */
    static class MutableClock extends Clock {
        private Instant instant;
        MutableClock(LocalDateTime utc) { this.instant = utc.toInstant(ZoneOffset.UTC); }
        void setTime(LocalDateTime utc) { this.instant = utc.toInstant(ZoneOffset.UTC); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private final Map<Long, Reservation> reservations = new HashMap<>();
    private final List<String> notifications = new ArrayList<>();
    private long nextId;
    private boolean notificationFails;
    private MutableClock clock;
    private ReservationService service;

    private static Reservation copy(Reservation r) {
        return new Reservation(r.getId(), r.getResourceId(), r.getUserId(), r.getStartTime(),
                r.getEndTime(), r.getParticipantCount(), r.getState());
    }

    @BeforeEach
    void setUp() {
        reservations.clear();
        notifications.clear();
        nextId = 1L;
        notificationFails = false;
        clock = new MutableClock(LocalDateTime.of(2026, 10, 1, 7, 0)); // server 07:00

        Resource u1 = new Resource(1L, "U1", 30);

        ReservationRepository reservationRepository = new ReservationRepository() {
            @Override
            public Reservation save(Reservation r) {
                Long id = r.getId() != null ? r.getId() : nextId++;
                Reservation stored = new Reservation(id, r.getResourceId(), r.getUserId(), r.getStartTime(),
                        r.getEndTime(), r.getParticipantCount(), r.getState());
                reservations.put(id, stored);
                return copy(stored);
            }

            @Override
            public Optional<Reservation> findById(Long id) {
                return Optional.ofNullable(reservations.get(id)).map(ReservationServiceTest::copy);
            }

            @Override
            public List<Reservation> findConfirmedByResourceAndTimeRange(Long resourceId,
                                                                         LocalDateTime start, LocalDateTime end) {
                return reservations.values().stream()
                        .filter(r -> r.getState() == ReservationState.CONFIRMED)
                        .filter(r -> r.getResourceId().equals(resourceId))
                        .filter(r -> r.getStartTime().isBefore(end) && start.isBefore(r.getEndTime()))
                        .map(ReservationServiceTest::copy)
                        .toList();
            }

            @Override
            public List<Reservation> findByUserId(String userId) {
                return reservations.values().stream()
                        .filter(r -> r.getUserId().equals(userId))
                        .sorted(Comparator.comparing(Reservation::getStartTime))
                        .map(ReservationServiceTest::copy)
                        .toList();
            }
        };

        ResourceRepository resourceRepository = new ResourceRepository() {
            @Override
            public Optional<Resource> findById(Long id) {
                return Optional.of(u1).filter(r -> r.getId().equals(id));
            }

            @Override
            public List<Resource> findAll() {
                return List.of(u1);
            }
        };

        NotificationPort notificationPort = new NotificationPort() {
            @Override
            public void notifyConfirmed(Reservation r) {
                if (notificationFails) throw new RuntimeException("Notification Service nedostupná");
                notifications.add("CONFIRMED:" + r.getId());
            }

            @Override
            public void notifyCancelled(Reservation r) {
                if (notificationFails) throw new RuntimeException("Notification Service nedostupná");
                notifications.add("CANCELLED:" + r.getId());
            }
        };

        service = new ReservationService(reservationRepository, resourceRepository, notificationPort, clock);
    }

    private Reservation createDraft(LocalDateTime start, LocalDateTime end, int participants) {
        return service.createReservation(1L, OWNER, OWNER, start, end, participants);
    }

    private Reservation createConfirmed(LocalDateTime start, LocalDateTime end) {
        Reservation draft = createDraft(start, end, 10);
        return service.confirmReservation(draft.getId(), OWNER);
    }

    // ---------- Create ----------

    @Test
    void create_storesDraft_andDoesNotNotify() {
        Reservation r = createDraft(T10, T10.plusHours(1), 20);

        assertNotNull(r.getId());
        assertEquals(ReservationState.DRAFT, r.getState());
        assertTrue(notifications.isEmpty());
    }

    @Test
    void create_isNotBlockedByExistingConfirmedReservation() {
        createConfirmed(T10, T10.plusHours(1));

        Reservation second = createDraft(T10, T10.plusHours(1), 5);

        assertEquals(ReservationState.DRAFT, second.getState());
    }

    @Test
    void create_rejectsUnknownResource_withoutStoringAnything() {
        assertThrows(ReservationNotFoundException.class, () ->
                service.createReservation(99L, OWNER, OWNER, T10, T10.plusHours(1), 10));
        assertTrue(reservations.isEmpty());
    }

    @Test
    void create_rejectsOverCapacityAndForeignOwner_withoutStoringAnything() {
        assertThrows(ReservationBusinessRuleException.class, () -> createDraft(T10, T10.plusHours(1), 31));
        assertThrows(UnauthorizedReservationException.class, () ->
                service.createReservation(1L, OTHER, OWNER, T10, T10.plusHours(1), 10));
        assertTrue(reservations.isEmpty());
    }

    // ---------- Confirm ----------

    @Test
    void confirm_succeeds_blocksResource_andNotifies() {
        Reservation confirmed = createConfirmed(T10, T10.plusHours(1));

        assertEquals(ReservationState.CONFIRMED, confirmed.getState());
        assertFalse(service.checkAvailability(1L, OTHER, T10, T10.plusHours(1)));
        assertEquals(List.of("CONFIRMED:" + confirmed.getId()), notifications);
    }

    @Test
    void confirm_rejectsConflict_keepsDraft_andDoesNotNotify() {
        createConfirmed(T10, T10.plusHours(1));
        notifications.clear();
        Reservation conflicting = createDraft(T10.plusMinutes(30), T10.plusMinutes(90), 5);

        assertThrows(ReservationBusinessRuleException.class,
                () -> service.confirmReservation(conflicting.getId(), OWNER));

        assertEquals(ReservationState.DRAFT, reservations.get(conflicting.getId()).getState());
        assertTrue(notifications.isEmpty());
    }

    @Test
    void confirm_succeedsForBackToBackIntervals() {
        createConfirmed(T10, T10.plusHours(1));
        Reservation next = createDraft(T10.plusHours(1), T10.plusHours(2), 5);

        assertDoesNotThrow(() -> service.confirmReservation(next.getId(), OWNER));
    }

    @Test
    void confirm_rejectsForeignUser_andUnknownReservation() {
        Reservation draft = createDraft(T10, T10.plusHours(1), 10);

        assertThrows(UnauthorizedReservationException.class, () -> service.confirmReservation(draft.getId(), OTHER));
        assertThrows(ReservationNotFoundException.class, () -> service.confirmReservation(999L, OWNER));
        assertEquals(ReservationState.DRAFT, reservations.get(draft.getId()).getState());
    }

    @Test
    void confirm_rejectsAtStart() {
        Reservation draft = createDraft(T10, T10.plusHours(1), 10);
        clock.setTime(T10); // hranice začátku

        assertThrows(ReservationBusinessRuleException.class, () -> service.confirmReservation(draft.getId(), OWNER));
        assertEquals(ReservationState.DRAFT, reservations.get(draft.getId()).getState());
    }

    @Test
    void confirm_rejectsAlreadyConfirmedReservation() {
        Reservation confirmed = createConfirmed(T10, T10.plusHours(1));

        assertThrows(InvalidReservationStateException.class,
                () -> service.confirmReservation(confirmed.getId(), OWNER));
    }

    @Test
    void confirm_staysSuccessful_whenNotificationFails() {
        notificationFails = true;
        Reservation draft = createDraft(T10, T10.plusHours(1), 10);

        Reservation confirmed = service.confirmReservation(draft.getId(), OWNER);

        assertEquals(ReservationState.CONFIRMED, confirmed.getState());
        assertEquals(ReservationState.CONFIRMED, reservations.get(draft.getId()).getState());
        assertTrue(notifications.isEmpty()); // zahozeno bez opakování
    }

    // ---------- Cancel ----------

    @Test
    void cancel_draft_keepsRecord_andNotifies() {
        Reservation draft = createDraft(T10, T10.plusHours(1), 10);

        Reservation cancelled = service.cancelReservation(draft.getId(), OWNER);

        assertEquals(ReservationState.CANCELLED, cancelled.getState());
        assertEquals(10, reservations.get(draft.getId()).getParticipantCount());
        assertEquals(List.of("CANCELLED:" + draft.getId()), notifications);
    }

    @Test
    void cancel_confirmed_releasesTheInterval() {
        Reservation confirmed = createConfirmed(T10, T10.plusHours(1));
        assertFalse(service.checkAvailability(1L, OTHER, T10, T10.plusHours(1)));

        service.cancelReservation(confirmed.getId(), OWNER);

        assertTrue(service.checkAvailability(1L, OTHER, T10, T10.plusHours(1)));
    }

    @Test
    void cancel_repeated_isRejected_withoutNewNotification() {
        Reservation draft = createDraft(T10, T10.plusHours(1), 10);
        service.cancelReservation(draft.getId(), OWNER);
        notifications.clear();

        assertThrows(InvalidReservationStateException.class, () -> service.cancelReservation(draft.getId(), OWNER));
        assertTrue(notifications.isEmpty());
    }

    @Test
    void cancel_rejectsForeignUser_unknownId_andLateCancellation() {
        Reservation draft = createConfirmed(T10, T10.plusHours(1));

        assertThrows(UnauthorizedReservationException.class, () -> service.cancelReservation(draft.getId(), OTHER));
        assertThrows(ReservationNotFoundException.class, () -> service.cancelReservation(999L, OWNER));

        clock.setTime(T10.minusHours(2).plusSeconds(1));
        assertThrows(ReservationBusinessRuleException.class, () -> service.cancelReservation(draft.getId(), OWNER));
        assertEquals(ReservationState.CONFIRMED, reservations.get(draft.getId()).getState());
    }

    @Test
    void cancel_staysSuccessful_whenNotificationFails() {
        Reservation draft = createDraft(T10, T10.plusHours(1), 10);
        notificationFails = true;

        Reservation cancelled = service.cancelReservation(draft.getId(), OWNER);

        assertEquals(ReservationState.CANCELLED, cancelled.getState());
    }

    // ---------- Check availability ----------

    @Test
    void availability_followsSpecificationExamples() {
        createConfirmed(T10, T10.plusHours(1)); // U1 [10:00,11:00)

        assertTrue(service.checkAvailability(1L, OTHER, T10.minusHours(1), T10));                 // [09:00,10:00)
        assertFalse(service.checkAvailability(1L, OTHER, T10.plusMinutes(30), T10.plusMinutes(90))); // [10:30,11:30)
        assertTrue(service.checkAvailability(1L, OTHER, T10.plusHours(1), T10.plusHours(2)));      // [11:00,12:00)
        assertFalse(service.checkAvailability(1L, OTHER, T10, T10.plusHours(1)));                  // totožný
        assertFalse(service.checkAvailability(1L, OTHER, T10.plusMinutes(15), T10.plusMinutes(45))); // uvnitř
    }

    @Test
    void availability_ignoresDraftAndCancelled_andOtherResources() {
        createDraft(T10, T10.plusHours(1), 10);
        Reservation toCancel = createDraft(T10, T10.plusHours(1), 10);
        service.cancelReservation(toCancel.getId(), OWNER);

        assertTrue(service.checkAvailability(1L, OTHER, T10, T10.plusHours(1)));
    }

    @Test
    void availability_rejectsUnknownResource_invalidInterval_andUnauthenticatedUser() {
        assertThrows(ReservationNotFoundException.class,
                () -> service.checkAvailability(99L, OTHER, T10, T10.plusHours(1)));
        assertThrows(ReservationValidationException.class,
                () -> service.checkAvailability(1L, OTHER, T10, T10));
        assertThrows(ReservationValidationException.class,
                () -> service.checkAvailability(1L, OTHER, T10, null));
        assertThrows(UnauthorizedReservationException.class,
                () -> service.checkAvailability(1L, " ", T10, T10.plusHours(1)));
    }

    @Test
    void availability_doesNotRequireTwoHourLeadTime_andChangesNothing() {
        clock.setTime(T10.plusHours(5)); // interval je v minulosti
        int before = reservations.size();

        assertTrue(service.checkAvailability(1L, OTHER, T10, T10.plusHours(1)));
        assertEquals(before, reservations.size());
    }

    // ---------- Seznamy ----------

    @Test
    void listReservations_returnsOnlyOwnReservations_sortedByStart_includingCancelled() {
        Reservation later = createDraft(T10.plusHours(3), T10.plusHours(4), 5);
        createDraft(T10, T10.plusHours(1), 5);
        service.cancelReservation(later.getId(), OWNER);
        service.createReservation(1L, OTHER, OTHER, T10, T10.plusHours(1), 5); // cizí

        List<Reservation> mine = service.listReservations(OWNER);

        assertEquals(2, mine.size());
        assertEquals(T10, mine.get(0).getStartTime());
        assertEquals(ReservationState.CANCELLED, mine.get(1).getState());
        assertTrue(mine.stream().allMatch(r -> r.getUserId().equals(OWNER)));
    }

    @Test
    void listReservations_isEmptyForUserWithoutReservations() {
        createDraft(T10, T10.plusHours(1), 5);

        assertTrue(service.listReservations(OTHER).isEmpty());
    }

    @Test
    void lists_rejectUnauthenticatedUser() {
        assertThrows(UnauthorizedReservationException.class, () -> service.listReservations(" "));
        assertThrows(UnauthorizedReservationException.class, () -> service.listResources(null));
    }

    @Test
    void listResources_returnsClassrooms() {
        List<Resource> resources = service.listResources(OWNER);

        assertEquals(1, resources.size());
        assertEquals("U1", resources.get(0).getLabel());
    }
}