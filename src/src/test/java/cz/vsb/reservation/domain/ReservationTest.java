package cz.vsb.reservation.domain;

import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.model.ReservationState;
import cz.vsb.reservation.domain.model.Resource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {

    private final Resource resource = new Resource(1L, "Učebna A1", 20);

    private Reservation newDraft(int participantCount) {
        return Reservation.createDraft(1L, "user-123",
                LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0),
                participantCount);
    }

    @Test
    void confirm_succeeds_whenParticipantsFitCapacity() {
        Reservation reservation = newDraft(15);

        reservation.confirm(resource);

        assertEquals(ReservationState.CONFIRMED, reservation.getState());
    }

    @Test
    void confirm_throwsAndStaysDraft_whenParticipantsExceedCapacity() {
        Reservation reservation = newDraft(25);

        assertThrows(IllegalStateException.class, () -> reservation.confirm(resource));
        assertEquals(ReservationState.DRAFT, reservation.getState());
    }

    @Test
    void cancel_isAllowed_fromConfirmed() {
        Reservation reservation = newDraft(10);
        reservation.confirm(resource);

        reservation.cancel();

        assertEquals(ReservationState.CANCELLED, reservation.getState());
    }

    @Test
    void confirm_throws_whenAlreadyCancelled() {
        Reservation reservation = newDraft(10);
        reservation.cancel();

        assertThrows(IllegalStateException.class, () -> reservation.confirm(resource));
    }

    @Test
    void constructor_throws_whenEndTimeBeforeStartTime() {
        assertThrows(IllegalArgumentException.class, () ->
                Reservation.createDraft(1L, "user-123",
                        LocalDateTime.of(2026, 10, 1, 11, 0),
                        LocalDateTime.of(2026, 10, 1, 10, 0),
                        5));
    }
}