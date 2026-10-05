package cz.vsb.reservation.domain.model;

public enum ReservationState {
    DRAFT, PENDING_APPROVAL, CONFIRMED, CANCELLED, REJECTED, EXPIRED;

    public boolean canTransitionTo(ReservationState target) {
        return switch (this) {
            case DRAFT -> target == CONFIRMED || target == PENDING_APPROVAL || target == CANCELLED;
            case PENDING_APPROVAL -> target == CONFIRMED || target == CANCELLED || target == REJECTED || target == EXPIRED;
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED, REJECTED, EXPIRED -> false;
        };
    }
}
