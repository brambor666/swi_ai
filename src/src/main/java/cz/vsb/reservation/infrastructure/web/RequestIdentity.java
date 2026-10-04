package cz.vsb.reservation.infrastructure.web;

/**
 * Identitu ověřuje externí Identity Provider (předpoklad z intent-and-change.md).
 * Dokud ho nemáme, jeho roli zastupuje hlavička X-User-Id. Až přijde JWT,
 * změní se jen tato jedna třída.
 */
final class RequestIdentity {

    static final String USER_HEADER = "X-User-Id";

    private RequestIdentity() {
    }

    static String require(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new MissingIdentityException();
        }
        return headerValue;
    }
}