package co.istad.projectpracticum.phsardigital.core.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The developer-facing half of an error body: what was thrown, where it came
 * from inside this application, and the chain of causes underneath it. Only
 * attached while {@code app.error.include-exception-details} is enabled, because
 * it names internal classes and line numbers.
 *
 * @param type    fully qualified exception class
 * @param message the exception message
 * @param origin  first stack frame belonging to this application, as class.method(File.java:line)
 * @param causes  the cause chain, outermost first, each as "type: message"
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExceptionDetailResponse(
        String type,
        String message,
        String origin,
        List<String> causes
) {
}
