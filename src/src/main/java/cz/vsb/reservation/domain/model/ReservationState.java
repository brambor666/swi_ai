package cz.vsb.reservation.domain.model;

/**
 * Stavy rezervace podle README.md:
 * DRAFT — návrh, CONFIRMED — potvrzená, CANCELLED — zrušená.
 */
public enum ReservationState {
    DRAFT,
    CONFIRMED,
    CANCELLED;

    /**
     * Povolené přechody podle intent-and-change.md:
     * Potvrzení: DRAFT -> CONFIRMED
     * Zrušení:   DRAFT nebo CONFIRMED -> CANCELLED
     */
    public boolean canTransitionTo(ReservationState target) {
        return switch (this) {
            case DRAFT -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED -> false; // konečný stav, žádný další přechod není povolen
        };
    }
}