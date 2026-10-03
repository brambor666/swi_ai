package cz.vsb.reservation.infrastructure.web;

import cz.vsb.reservation.domain.exception.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class RestExceptionHandler {

    @ExceptionHandler(MissingIdentityException.class)
    ResponseEntity<ErrorResponse> handleMissingIdentity(MissingIdentityException e) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", e.getMessage());
    }

    @ExceptionHandler(ReservationValidationException.class)
    ResponseEntity<ErrorResponse> handleValidation(ReservationValidationException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Neplatný požadavek");
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message);
    }

    /** Např. chybný formát data nebo desetinný počet účastníků (1,5). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Požadavek nelze přečíst (zkontroluj formát hodnot)");
    }

    @ExceptionHandler(UnauthorizedReservationException.class)
    ResponseEntity<ErrorResponse> handleForbidden(UnauthorizedReservationException e) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(ReservationNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(InvalidReservationStateException.class)
    ResponseEntity<ErrorResponse> handleState(InvalidReservationStateException e) {
        return error(HttpStatus.CONFLICT, "INVALID_STATE", e.getMessage());
    }

    @ExceptionHandler(ReservationBusinessRuleException.class)
    ResponseEntity<ErrorResponse> handleBusinessRule(ReservationBusinessRuleException e) {
        return error(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", e.getMessage());
    }

    /** ADR-002: databázový EXCLUDE constraint zachytil souběh, který doménová kontrola nestihla. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraint(DataIntegrityViolationException e) {
        return error(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION",
                "Rezervace se překrývá s jinou potvrzenou rezervací stejné učebny");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                "Chybí povinný parametr: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                "Neplatná hodnota parametru: " + e.getName());
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}