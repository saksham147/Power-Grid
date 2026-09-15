package Grid.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import Grid.api.dto.ApiError;

/** Turns the exceptions this API raises into {@link ApiError} responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "Request body failed validation", details));
    }

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
