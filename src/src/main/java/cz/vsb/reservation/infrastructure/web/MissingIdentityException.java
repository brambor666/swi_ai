package cz.vsb.reservation.infrastructure.web;

/** Chybí identita ověřeného uživatele → HTTP 401. */
class MissingIdentityException extends RuntimeException {
    MissingIdentityException() {
        super("Chybí identita ověřeného uživatele (hlavička X-User-Id)");
    }
}