package Grid.api.dto;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * The single error shape for this API.
 *
 * @param timestamp when the failure was handled
 * @param status    HTTP status code, repeated in the body so a logged response stands on its own
 * @param error     the status reason phrase
 * @param message   what went wrong
 * @param details   per-field messages for a validation failure; empty otherwise
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details) {

    public static ApiError of(HttpStatus status, String message) {
        return of(status, message, List.of());
    }

    public static ApiError of(HttpStatus status, String message, List<String> details) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, details);
    }
}
