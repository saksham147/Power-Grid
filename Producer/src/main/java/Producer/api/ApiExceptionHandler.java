package Producer.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import Producer.api.dto.ApiError;

/**
 * Turns the exceptions this API raises into {@link ApiError} responses.
 *
 * <p>
 * Without this every failure would surface as Spring's default error body,
 * which reports a 500
 * for cases that are plainly the caller's doing -- an unknown plant id, a
 * negative interval,
 * starting a run that is already going.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PlantNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(PlantNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * 409 rather than 400: starting an already-running simulation is not a
     * malformed request, it is
     * a request that conflicts with the current state and would succeed once
     * stopped.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException e) {
        return respond(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadArgument(IllegalArgumentException e) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "Request body failed validation", details));
    }

    /**
     * Covers a malformed body and, more usefully, an unknown enum value -- posting
     * a plant with
     * type {@code NUCLEAR} fails here, and the cause message names the accepted
     * values.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();
        return respond(HttpStatus.BAD_REQUEST, "Malformed request body: " + cause.getMessage());
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String message) {
        log.debug("Responding {} : {}", status.value(), message);
        return ResponseEntity.status(status).body(ApiError.of(status, message));
    }
}
