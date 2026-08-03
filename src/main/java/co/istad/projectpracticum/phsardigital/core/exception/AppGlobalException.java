package co.istad.projectpracticum.phsardigital.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class AppGlobalException {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<RestErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null
                ? status.getReasonPhrase()
                : exception.getReason();
        return ResponseEntity.status(status).body(
                buildError(status, message, null)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        List<FieldResponse> fields = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldResponse)
                .toList();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(
                buildError(status, "Request validation failed.", fields)
        );
    }

    private FieldResponse toFieldResponse(FieldError error) {
        return new FieldResponse(error.getField(), error.getDefaultMessage());
    }

    private RestErrorResponse buildError(HttpStatus status, String message, Object details) {
        return RestErrorResponse.builder()
                .message(message)
                .code(status.value())
                .status(status.getReasonPhrase())
                .timestamp(Instant.now())
                .errorDetails(details)
                .build();
    }

}
