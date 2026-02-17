package com.buildquote.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for IFC processing errors.
 * Returns Estonian error messages.
 */
@RestControllerAdvice
@Slf4j
public class IfcExceptionHandler {

    @ExceptionHandler(InvalidIfcFileException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidIfcFile(InvalidIfcFileException e) {
        log.warn("Invalid IFC file: {}", e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Vigane IFC fail");
        response.put("message", "Vigane IFC fail. Palun kontrolli et fail on korrektne .ifc formaadis.");
        response.put("details", e.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IfcFileTooLargeException.class)
    public ResponseEntity<Map<String, Object>> handleFileTooLarge(IfcFileTooLargeException e) {
        log.warn("IFC file too large: {}MB, max: {}MB", e.getFileSizeMb(), e.getMaxSizeMb());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Fail on liiga suur");
        response.put("message", String.format(
            "Fail on liiga suur (%dMB). Maksimaalne lubatud suurus on %dMB.",
            e.getFileSizeMb(), e.getMaxSizeMb()
        ));
        response.put("fileSizeMb", e.getFileSizeMb());
        response.put("maxSizeMb", e.getMaxSizeMb());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(IfcProcessingTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeout(IfcProcessingTimeoutException e) {
        log.error("IFC processing timeout: {}", e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Töötlemise ajalõpp");
        response.put("message", String.format(
            "IFC faili töötlemine võttis liiga kaua (%ds). Proovi väiksema failiga.",
            e.getTimeoutSeconds()
        ));
        response.put("timeoutSeconds", e.getTimeoutSeconds());

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
    }

    @ExceptionHandler(IfcParseException.class)
    public ResponseEntity<Map<String, Object>> handleParseException(IfcParseException e) {
        log.error("IFC parse error: {}", e.getMessage());
        if (e.getPythonError() != null) {
            log.error("Python error output: {}", e.getPythonError());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("error", "IFC parsimise viga");
        response.put("message", "IFC faili töötlemine ebaõnnestus: " + e.getMessage());

        if (e.getPythonError() != null && !e.getPythonError().isBlank()) {
            // Include first line of Python error for debugging
            String pythonError = e.getPythonError();
            String firstLine = pythonError.split("\n")[0];
            response.put("details", firstLine);
        }

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }
}
