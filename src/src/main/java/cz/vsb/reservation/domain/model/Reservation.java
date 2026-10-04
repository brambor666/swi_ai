package cz.vsb.reservation.domain.model;

import cz.vsb.reservation.domain.exception.InvalidReservationStateException;
import cz.vsb.reservation.domain.exception.ReservationBusinessRuleException;
import cz.vsb.reservation.domain.exception.ReservationValidationException;
import cz.vsb.reservation.domain.exception.UnauthorizedReservationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

public final class Reservation {

    /** BR-04: Create, Confirm a Cancel povoleny jen nejméně 2 hodiny před začátkem (čas serveru, UTC). */
    private static final Duration MIN_LEAD_TIME = Duration.ofHours(2);

    private final Long id;
    private final Long resourceId;
    private final String userId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final int participantCount;
    private ReservationState state;

    public Reservation(Long id, Long resourceId, String userId,
                       LocalDateTime startTime, LocalDateTime endTime,
                       int participantCount, ReservationState state) {
        if (userId == null || userId.isBlank()) {
            throw new ReservationValidationException("userId nesmí být prázdné");
        }
        if (startTime == null || endTime == null) {
            throw new ReservationValidationException("Časový interval musí být kompletní");
        }
        if (!endTime.isAfter(startTime)) {
            throw new ReservationValidationException("Konec rezervace musí být po jejím začátku");
        }
        if (participantCount <= 0) {
            throw new ReservationValidationException("Počet účastníků musí být kladné číslo");
        }
        this.id = id;
        this.resourceId = resourceId;
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.participantCount = participantCount;
        this.state = Objects.requireNonNull(state);
    }

    /**
     * OP-01 / BR-03, BR-04, BR-05: vytvoření návrhu.
     * requestingUserId = kdo žádost odeslal; musí odpovídat ownerUserId (BR-05).
     * now = aktuální čas serveru v UTC (BR-04).
     */
    public static Reservation createDraft(Resource resource, String requestingUserId, String ownerUserId,
                                          LocalDateTime start, LocalDateTime end,
                                          int participantCount, LocalDateTime now) {

        if (start == null || end == null) {
            throw new ReservationValidationException("Časový interval musí být kompletní");
        }

        if (!Objects.equals(requestingUserId, ownerUserId)) {
            throw new UnauthorizedReservationException(
                    "Rezervaci lze vytvořit pouze pro sebe");
        }
        if (!meetsLeadTime(start, now)) {
            throw new ReservationBusinessRuleException(
                    "Vytvoření je povoleno nejméně 2 hodiny před začátkem");
        }
        if (!resource.canAccommodate(participantCount)) {
            throw new ReservationBusinessRuleException(
                    "Počet účastníků (%d) překračuje kapacitu učebny (%d)"
                            .formatted(participantCount, resource.getCapacity()));
        }
        return new Reservation(null, resource.getId(), ownerUserId, start, end,
                participantCount, ReservationState.DRAFT);
    }

    /**
     * OP-03 / BR-03, BR-04, BR-05: potvrzení. Kontrola konfliktu (BR-02) zůstává
     * v ReservationService, protože vyžaduje pohled na ostatní rezervace.
     */
    public void confirm(Resource resource, String requestingUserId, LocalDateTime now) {
        requireOwner(requestingUserId);
        if (!meetsLeadTime(startTime, now)) {
            throw new ReservationBusinessRuleException(
                    "Potvrzení je povoleno nejméně 2 hodiny před začátkem");
        }
        transitionTo(ReservationState.CONFIRMED);
        if (!resource.canAccommodate(participantCount)) {
            this.state = ReservationState.DRAFT; // rollback, přechod se neprovede napůl
            throw new ReservationBusinessRuleException(
                    "Počet účastníků (%d) překračuje kapacitu učebny (%d)"
                            .formatted(participantCount, resource.getCapacity()));
        }
    }

    /** OP-04 / BR-04, BR-05: zrušení. */
    public void cancel(String requestingUserId, LocalDateTime now) {
        requireOwner(requestingUserId);
        if (!meetsLeadTime(startTime, now)) {
            throw new ReservationBusinessRuleException(
                    "Zrušení je povoleno nejméně 2 hodiny před začátkem");
        }
        transitionTo(ReservationState.CANCELLED);
    }

    /** BR-04: now <= start - 2h. Přesná hranice (==) je povolena. */
    private static boolean meetsLeadTime(LocalDateTime start, LocalDateTime now) {
        return !now.isAfter(start.minus(MIN_LEAD_TIME));
    }

    private void requireOwner(String requestingUserId) {
        if (!Objects.equals(requestingUserId, this.userId)) {
            throw new UnauthorizedReservationException(
                    "Operaci lze provést pouze nad vlastní rezervací");
        }
    }

    private void transitionTo(ReservationState target) {
        if (!state.canTransitionTo(target)) {
            throw new InvalidReservationStateException(
                    "Přechod z %s do %s není povolen".formatted(state, target));
        }
        this.state = target;
    }

    public Long getId() { return id; }
    public Long getResourceId() { return resourceId; }
    public String getUserId() { return userId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public int getParticipantCount() { return participantCount; }
    public ReservationState getState() { return state; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reservation other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}