package cz.vsb.reservation.domain.port.out;

import cz.vsb.reservation.domain.model.Reservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {

    default void expirePending(LocalDateTime now) {}

    default List<Reservation> findPendingApprovals() { return List.of(); }

    Reservation save(Reservation reservation);

    Optional<Reservation> findById(Long id);

    /** Načtení pro změnu; produkční adaptér drží zámek do konce transakce. */
    default Optional<Reservation> findByIdForUpdate(Long id) {
        return findById(id);
    }

    /**
     * Potřebné pro ADR-002 (kontrola překryvu) — service vrstva si natáhne
     * existující CONFIRMED rezervace dané učebny v daném okně a ověří překryv
     * v doméně, než se spolehne na databázový EXCLUDE constraint jako
     * poslední pojistku proti souběhu.
     */
    List<Reservation> findConfirmedByResourceAndTimeRange(Long resourceId, LocalDateTime start, LocalDateTime end);

    /** Rezervace daného uživatele ve všech stavech, seřazené podle začátku. */
    List<Reservation> findByUserId(String userId);
}