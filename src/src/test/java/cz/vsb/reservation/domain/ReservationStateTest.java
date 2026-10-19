package cz.vsb.reservation.domain;

import cz.vsb.reservation.domain.model.ReservationState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static cz.vsb.reservation.domain.model.ReservationState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReservationStateTest {

    /** Statechart specifikace v0.2: povolené přechody. Všechno ostatní je zakázané. */
    private static final Map<ReservationState, Set<ReservationState>> ALLOWED = Map.of(
            DRAFT, Set.of(CONFIRMED, PENDING_APPROVAL, CANCELLED),
            PENDING_APPROVAL, Set.of(CONFIRMED, CANCELLED, REJECTED, EXPIRED),
            CONFIRMED, Set.of(CANCELLED),
            CANCELLED, Set.of(),
            REJECTED, Set.of(),
            EXPIRED, Set.of());

    @Test
    void everyTransitionMatchesTheStatechart() {
        for (ReservationState from : ReservationState.values()) {
            for (ReservationState to : ReservationState.values()) {
                assertEquals(ALLOWED.get(from).contains(to), from.canTransitionTo(to), from + " -> " + to);
            }
        }
    }
}