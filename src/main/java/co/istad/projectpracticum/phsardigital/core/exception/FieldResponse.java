package co.istad.projectpracticum.phsardigital.core.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One rejected input. {@code rejectedValue} is what the caller actually sent, so
 * a client can highlight the offending field without guessing.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldResponse(
        String field,
        String fieldMessage,
        Object rejectedValue
) {

    public FieldResponse(String field, String fieldMessage) {
        this(field, fieldMessage, null);
    }
}
