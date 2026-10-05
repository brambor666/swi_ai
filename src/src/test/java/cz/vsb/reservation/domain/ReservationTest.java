package cz.vsb.reservation.domain;

import cz.vsb.reservation.domain.exception.InvalidReservationStateException;
import cz.vsb.reservation.domain.exception.ReservationBusinessRuleException;
import cz.vsb.reservation.domain.exception.ReservationValidationException;
import cz.vsb.reservation.domain.exception.UnauthorizedReservationException;
import cz.vsb.reservation.domain.model.Reservation;
import cz.vsb.reservation.domain.model.ReservationState;
import cz.vsb.reservation.domain.model.Resource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {

    private static final String OWNER = "user-1";
    private static final String OTHER = "user-2";
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 7, 0); // server 07:00, začátek 10:00

    private final Resource resource = new Resource(1L, "U1", 30);

    private Reservation draft(int participants) {
        return Reservation.createDraft(resource, OWNER, OWNER, START, END, participants, NOW);
    }

    private Reservation existing(int participants, ReservationState state) {
        return new Reservation(1L, 1L, OWNER, START, END, participants, state);
    }

    // ---------- Create (OP-01) ----------

    @Test
    void createDraft_createsDraftWithGivenData() {
        Reservation r = draft(20);

        assertEquals(ReservationState.DRAFT, r.getState());
        assertEquals(1L, r.getResourceId());
        assertEquals(OWNER, r.getUserId());
        assertEquals(START, r.getStartTime());
        assertEquals(END, r.getEndTime());
        assertEquals(20, r.getParticipantCount());
    }

    @Test
    void createDraft_acceptsCapacityExactly_rejectsOneOver() {
        assertEquals(ReservationState.DRAFT, draft(30).getState());
        assertThrows(ReservationBusinessRuleException.class, () -> draft(31));
    }

    @Test
    void createDraft_acceptsJustBeforeStart() {
        LocalDateTime exactlyTwoHoursBefore = START.minusSeconds(1);

        Reservation r = Reservation.createDraft(resource, OWNER, OWNER, START, END, 10, exactlyTwoHoursBefore);

        assertEquals(ReservationState.DRAFT, r.getState());
    }

    @Test
    void createDraft_rejectsAtStart() {
        LocalDateTime oneSecondTooLate = START;

        assertThrows(ReservationBusinessRuleException.class, () ->
                Reservation.createDraft(resource, OWNER, OWNER, START, END, 10, oneSecondTooLate));
    }

    @Test
    void createDraft_rejectsStartInThePast() {
        LocalDateTime afterStart = START.plusMinutes(1);

        assertThrows(ReservationBusinessRuleException.class, () ->
                Reservation.createDraft(resource, OWNER, OWNER, START, END, 10, afterStart));
    }

    @Test
    void createDraft_rejectsOwnerDifferentFromRequestingUser() {
        assertThrows(UnauthorizedReservationException.class, () ->
                Reservation.createDraft(resource, OTHER, OWNER, START, END, 10, NOW));
    }

    @Test
    void createDraft_rejectsInvalidInterval() {
        assertThrows(ReservationValidationException.class, () ->
                Reservation.createDraft(resource, OWNER, OWNER, START, START, 10, NOW)); // start == end
        assertThrows(ReservationValidationException.class, () ->
                Reservation.createDraft(resource, OWNER, OWNER, START, null, 10, NOW));  // chybí konec
    }

    @Test
    void createDraft_rejectsNonPositiveParticipantCount() {
        assertThrows(ReservationValidationException.class, () -> draft(0));
        assertThrows(ReservationValidationException.class, () -> draft(-1));
    }

    @Test
    void createDraft_rejectsBlankUserId() {
        assertThrows(ReservationValidationException.class, () ->
                Reservation.createDraft(resource, " ", " ", START, END, 10, NOW));
    }

    // ---------- Confirm (OP-03) ----------

    @Test
    void confirm_movesDraftToConfirmed() {
        Reservation r = draft(30);

        r.confirm(resource, OWNER, NOW);

        assertEquals(ReservationState.CONFIRMED, r.getState());
    }

    @Test
    void confirm_rejectsOverCapacityDraft_andStaysDraft() {
        // Nadkapacitní DRAFT běžné Create odmítá, proto ho pro test sestavujeme přímo.
        Reservation r = existing(31, ReservationState.DRAFT);

        assertThrows(ReservationBusinessRuleException.class, () -> r.confirm(resource, OWNER, NOW));
        assertEquals(ReservationState.DRAFT, r.getState());
    }

    @Test
    void confirm_rejectsForeignUser_andStaysDraft() {
        Reservation r = draft(10);

        assertThrows(UnauthorizedReservationException.class, () -> r.confirm(resource, OTHER, NOW));
        assertEquals(ReservationState.DRAFT, r.getState());
    }

    @Test
    void confirm_allowsExactBoundary_rejectsOneSecondLater() {
        Reservation onTime = draft(10);
        onTime.confirm(resource, OWNER, START.minusSeconds(1));
        assertEquals(ReservationState.CONFIRMED, onTime.getState());

        Reservation tooLate = draft(10);
        assertThrows(ReservationBusinessRuleException.class, () ->
                tooLate.confirm(resource, OWNER, START));
        assertEquals(ReservationState.DRAFT, tooLate.getState());
    }

    @Test
    void confirm_rejectsAlreadyConfirmedOrCancelled() {
        Reservation confirmed = existing(10, ReservationState.CONFIRMED);
        Reservation cancelled = existing(10, ReservationState.CANCELLED);

        assertThrows(InvalidReservationStateException.class, () -> confirmed.confirm(resource, OWNER, NOW));
        assertThrows(InvalidReservationStateException.class, () -> cancelled.confirm(resource, OWNER, NOW));
        assertEquals(ReservationState.CONFIRMED, confirmed.getState());
        assertEquals(ReservationState.CANCELLED, cancelled.getState());
    }

    @Test
    void cancel_draftHasNoTimeLimit() {
        for (LocalDateTime now : new LocalDateTime[]{START.minusMinutes(1), START, END.plusDays(1)}) {
            Reservation r = existing(10, ReservationState.DRAFT);
            r.cancel(OWNER, now);
            assertEquals(ReservationState.CANCELLED, r.getState());
        }
    }

    // ---------- Cancel (OP-04) ----------

    @Test
    void cancel_isAllowedFromDraftAndConfirmed() {
        Reservation fromDraft = existing(10, ReservationState.DRAFT);
        Reservation fromConfirmed = existing(10, ReservationState.CONFIRMED);

        fromDraft.cancel(OWNER, NOW);
        fromConfirmed.cancel(OWNER, NOW);

        assertEquals(ReservationState.CANCELLED, fromDraft.getState());
        assertEquals(ReservationState.CANCELLED, fromConfirmed.getState());
    }

    @Test
    void cancel_keepsAllOtherData() {
        Reservation r = existing(10, ReservationState.CONFIRMED);

        r.cancel(OWNER, NOW);

        assertEquals(1L, r.getResourceId());
        assertEquals(OWNER, r.getUserId());
        assertEquals(START, r.getStartTime());
        assertEquals(END, r.getEndTime());
        assertEquals(10, r.getParticipantCount());
    }

    @Test
    void cancel_rejectsRepeatedCancellation() {
        Reservation r = existing(10, ReservationState.CANCELLED);

        assertThrows(InvalidReservationStateException.class, () -> r.cancel(OWNER, NOW));
    }

    @Test
    void cancel_rejectsForeignUser_andKeepsState() {
        Reservation r = existing(10, ReservationState.CONFIRMED);

        assertThrows(UnauthorizedReservationException.class, () -> r.cancel(OTHER, NOW));
        assertEquals(ReservationState.CONFIRMED, r.getState());
    }

    @Test
    void cancel_allowsExactBoundary_rejectsOneSecondLater_andAtOrAfterStart() {
        existing(10, ReservationState.CONFIRMED).cancel(OWNER, START.minusHours(2)); // projde

        Reservation tooLate = existing(10, ReservationState.CONFIRMED);
        assertThrows(ReservationBusinessRuleException.class, () ->
                tooLate.cancel(OWNER, START.minusHours(2).plusSeconds(1)));
        assertThrows(ReservationBusinessRuleException.class, () -> tooLate.cancel(OWNER, START));
        assertThrows(ReservationBusinessRuleException.class, () -> tooLate.cancel(OWNER, END.plusMinutes(1)));
        assertEquals(ReservationState.CONFIRMED, tooLate.getState());
    }
}