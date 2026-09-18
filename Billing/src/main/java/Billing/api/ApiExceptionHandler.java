package Billing.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import Billing.billing.InsufficientFundsException;
import Billing.billing.PlantTypeLockedException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ZoneNotBilledException.class)
    ResponseEntity<Map<String, String>> handleZoneNotBilled(ZoneNotBilledException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    /** 402: the one HTTP status whose name already says exactly what went wrong here. */
    @ExceptionHandler(InsufficientFundsException.class)
    ResponseEntity<Map<String, String>> handleInsufficientFunds(InsufficientFundsException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of("message", e.getMessage()));
    }

    /** 403: well-formed request, simply not yet permitted -- distinct from 402's "permitted, but
     *  can't afford it". */
    @ExceptionHandler(PlantTypeLockedException.class)
    ResponseEntity<Map<String, String>> handlePlantTypeLocked(PlantTypeLockedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid request", "details", details));
    }
}
