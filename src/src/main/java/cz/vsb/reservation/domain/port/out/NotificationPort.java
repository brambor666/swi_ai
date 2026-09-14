package cz.vsb.reservation.domain.port.out;

import cz.vsb.reservation.domain.model.Reservation;

/**
 * Boundary z README.md — Notification Service odesílá uživateli oznámení
 * o potvrzení nebo zrušení rezervace. Doména jen řekne "co se stalo",
 * implementace (log/e-mail) je adaptér mimo doménu.
 */
public interface NotificationPort {

    void notifyConfirmed(Reservation reservation);

    void notifyCancelled(Reservation reservation);
}