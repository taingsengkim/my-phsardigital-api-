package co.istad.projectpracticum.phsardigital.core.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

/**
 * The single error body every failed request returns, whether the failure came
 * from a controller, from bean validation, or from the security filter chain.
 * Null members are dropped so a simple error stays a small payload.
 *
 * @param message      what went wrong, phrased for the caller
 * @param code         numeric HTTP status, kept for clients that switch on it
 * @param status       HTTP reason phrase
 * @param timestamp    when the failure was rendered
 * @param path         request URI that failed
 * @param method       HTTP method that failed
 * @param traceId      value also written to the server log line for this failure
 * @param exception    exception class name, only filled when detailed errors are enabled
 * @param errorDetails field errors, cause chain, or whatever else explains the failure
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RestErrorResponse(
        String message,
        Integer code,
        String status,
        Instant timestamp,
        String path,
        String method,
        String traceId,
        String exception,
        Object errorDetails
) {
}
