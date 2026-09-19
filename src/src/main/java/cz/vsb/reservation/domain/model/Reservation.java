package cz.vsb.reservation.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public final class Reservation {

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
            throw new IllegalArgumentException("userId nesmí být prázdné");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Časový interval musí být kompletní");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Konec rezervace musí být po jejím začátku");
        }
        if (participantCount <= 0) {
            throw new IllegalArgumentException("Počet účastníků musí být kladné číslo");
        }
        this.id = id;
        this.resourceId = resourceId;
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.participantCount = participantCount;
        this.state = Objects.requireNonNull(state);
    }

    /** Nová rezervace vždy vzniká ve stavu DRAFT (viz intent-and-change.md). */
    public static Reservation createDraft(Long resourceId, String userId,
                                          LocalDateTime startTime, LocalDateTime endTime,
                                          int participantCount) {
        return new Reservation(null, resourceId, userId, startTime, endTime,
                participantCount, ReservationState.DRAFT);
    }

    /**
     * Potvrzení rezervace. Vyžaduje Resource, aby ověřilo doménové pravidlo
     * kapacity (README.md: "Počet účastníků rezervace nesmí překročit
     * kapacitu učebny.") — kontrola patří sem, ne do service vrstvy,
     * protože je to neoddělitelná součást přechodu do CONFIRMED.
     */
    public void confirm(Resource resource) {
        transitionTo(ReservationState.CONFIRMED);
        if (!resource.canAccommodate(participantCount)) {
            // Vracíme stav zpět — přechod se nesmí "napůl" provést.
            this.state = ReservationState.DRAFT;
            throw new IllegalStateException(
                    "Počet účastníků (%d) překračuje kapacitu učebny (%d)"
                            .formatted(participantCount, resource.getCapacity()));
        }
    }

    public void cancel() {
        transitionTo(ReservationState.CANCELLED);
    }

    private void transitionTo(ReservationState target) {
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Přechod z %s do %s není povolen".formatted(state, target));
        }
        this.state = target;
    }

    public Long getId() {
        return id;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public String getUserId() {
        return userId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public ReservationState getState() {
        return state;
    }

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