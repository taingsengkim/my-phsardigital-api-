package co.istad.projectpracticum.phsardigital.core.exception;

import lombok.Builder;

import java.time.Instant;

@Builder
public record RestErrorResponse(
        String message,
        Integer code,
        String status,
        Instant timestamp,
        Object errorDetails
) {
}
