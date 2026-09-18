package Distributor.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import Distributor.distribution.ZoneCapacityNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ZoneCapacityNotFoundException.class)
    ResponseEntity<Map<String, String>> handleZoneCapacityNotFound(ZoneCapacityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid request", "details", details));
    }
}
