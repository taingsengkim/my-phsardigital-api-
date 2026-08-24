package co.istad.projectpracticum.phsardigital.core.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Turns every failure into one {@link RestErrorResponse} shape.
 * <p>
 * Each handler exists to say something the default response could not: which
 * field was rejected and what value it held, which constraint the database
 * refused, which media types are actually accepted. Anything with no dedicated
 * handler still lands on {@link #handleUnexpectedException} rather than falling
 * through to Spring's generic {@code /error} body, so a client never has to
 * parse two different error formats.
 * <p>
 * Every response carries a {@code traceId} that is also written to the server
 * log line for the same failure, so a reported error can be found in the logs
 * without guessing from timestamps.
 */
@Slf4j
@RestControllerAdvice
public class AppGlobalException {

    private static final String APP_PACKAGE = "co.istad.projectpracticum.phsardigital";

    /**
     * Adds the exception type, its cause chain, and the first application stack
     * frame to error bodies. Keep it on while developing; turn it off in
     * production, where internal class names are not the caller's business.
     */
    @Value("${app.error.include-exception-details:false}")
    private boolean includeExceptionDetails;

    // ---------------------------------------------------------------- explicit

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<RestErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null
                ? status.getReasonPhrase()
                : exception.getReason();
        return respond(status, message, null, request, exception);
    }

    /**
     * Any other framework exception that already carries a status and a problem
     * detail. Without this the fallback below would flatten it into a 500.
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<RestErrorResponse> handleErrorResponseException(
            ErrorResponseException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String detail = exception.getBody().getDetail();
        return respond(status, detail == null ? status.getReasonPhrase() : detail, null, request, exception);
    }

    // -------------------------------------------------------------- validation

    /**
     * Covers {@code MethodArgumentNotValidException} too, since it is a
     * {@link BindException}. Global (class-level) errors are reported alongside
     * field errors so a cross-field rule such as "end date must follow start
     * date" is not silently dropped.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<RestErrorResponse> handleValidationException(
            BindException exception,
            HttpServletRequest request
    ) {
        List<FieldResponse> fields = new ArrayList<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.add(toFieldResponse(error)));
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> fields.add(toFieldResponse(error)));
        return respond(HttpStatus.BAD_REQUEST, "Request validation failed.", fields, request, exception);
    }

    /**
     * Raised when constraints sit directly on controller method parameters
     * (for example {@code @Min} on a {@code @RequestParam}).
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<RestErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        List<FieldResponse> fields = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> fields.add(
                    error instanceof FieldError fieldError
                            ? toFieldResponse(fieldError)
                            : new FieldResponse(
                                    parameter == null ? "request" : parameter,
                                    error.getDefaultMessage(),
                                    result.getArgument())));
        });
        exception.getCrossParameterValidationResults().forEach(error ->
                fields.add(new FieldResponse("request", error.getDefaultMessage())));
        return respond(HttpStatus.BAD_REQUEST, "Request validation failed.", fields, request, exception);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RestErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<FieldResponse> fields = exception.getConstraintViolations()
                .stream()
                .map(violation -> new FieldResponse(
                        violation.getPropertyPath().toString(),
                        violation.getMessage(),
                        violation.getInvalidValue()
                ))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "Request validation failed.", fields, request, exception);
    }

    // ------------------------------------------------------------ bad requests

    /**
     * Malformed or unparseable body. The most specific cause carries the useful
     * part — the JSON path and the value Jackson choked on — so it is passed
     * through instead of a flat "bad request".
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RestErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "Request body is missing or not readable.",
                List.of(new FieldResponse("body", firstLine(exception.getMostSpecificCause().getMessage()))),
                request,
                exception
        );
    }

    /**
     * A path variable or query parameter that cannot be converted, such as a
     * non-numeric id or an unknown enum constant.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RestErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        Class<?> required = exception.getRequiredType();
        String expected = required == null ? "the expected type" : describeType(required);
        return respond(
                HttpStatus.BAD_REQUEST,
                "'%s' is not a valid value for '%s'.".formatted(exception.getValue(), exception.getName()),
                List.of(new FieldResponse(
                        exception.getName(),
                        "Expected " + expected + ".",
                        exception.getValue()
                )),
                request,
                exception
        );
    }

    /**
     * Missing request parameter, header, cookie, or matrix variable.
     */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<RestErrorResponse> handleRequestBindingException(
            ServletRequestBindingException exception,
            HttpServletRequest request
    ) {
        List<FieldResponse> fields = exception instanceof MissingServletRequestParameterException missing
                ? List.of(new FieldResponse(
                        missing.getParameterName(),
                        "Required " + missing.getParameterType() + " parameter is missing."))
                : null;
        return respond(HttpStatus.BAD_REQUEST, exception.getMessage(), fields, request, exception);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<RestErrorResponse> handleMissingPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "Multipart part '%s' is required.".formatted(exception.getRequestPartName()),
                List.of(new FieldResponse(exception.getRequestPartName(), "This part is missing from the request.")),
                request,
                exception
        );
    }

    // ------------------------------------------------------------- negotiation

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RestErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.METHOD_NOT_ALLOWED,
                "%s is not supported on this endpoint.".formatted(exception.getMethod()),
                List.of(new FieldResponse("supportedMethods", String.join(", ", nullSafe(exception.getSupportedMethods())))),
                request,
                exception
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RestErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content type '%s' is not supported.".formatted(exception.getContentType()),
                List.of(new FieldResponse("supportedMediaTypes", exception.getSupportedMediaTypes().toString())),
                request,
                exception
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<RestErrorResponse> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.NOT_ACCEPTABLE,
                "No representation matches the Accept header.",
                List.of(new FieldResponse("supportedMediaTypes", exception.getSupportedMediaTypes().toString())),
                request,
                exception
        );
    }

    /**
     * An unmapped URL. Without this the caller gets Spring's own 404 body, which
     * does not match the shape used everywhere else.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RestErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.NOT_FOUND,
                "No endpoint %s %s.".formatted(request.getMethod(), request.getRequestURI()),
                null,
                request,
                exception
        );
    }

    // ------------------------------------------------------------------ upload

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<RestErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        long maxBytes = exception.getMaxUploadSize();
        String limit = maxBytes < 0 ? "the configured limit" : maxBytes / (1024 * 1024) + "MB";
        return respond(
                HttpStatus.CONTENT_TOO_LARGE,
                "Upload is larger than " + limit + ".",
                null,
                request,
                exception
        );
    }

    // ---------------------------------------------------------------- database

    /** A versioned category/listing was changed after this request read it. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<RestErrorResponse> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.CONFLICT,
                "This resource was changed by another request. Reload it and try again.",
                null,
                request,
                exception
        );
    }

    /** A short-lived row-lock collision is retryable, not an internal server error. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<RestErrorResponse> handlePessimisticLockingFailure(
            PessimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.CONFLICT,
                "This resource is being changed by another request. Try again shortly.",
                null,
                request,
                exception
        );
    }

    /** A database constraint conflict, without exposing schema or submitted values. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RestErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.CONFLICT,
                "The request conflicts with data that already exists.",
                List.of(new FieldResponse(
                        "request",
                        "Check duplicate values and records referenced by this request.")),
                request,
                exception
        );
    }

    // ---------------------------------------------------------------- security

    /**
     * Denials raised inside the application (method security) and denials
     * routed back here from the security filter chain both arrive as
     * {@link AccessDeniedException}. An anonymous caller gets 401 — they may
     * simply not have sent a token — while an authenticated one gets 403 plus
     * the authorities their token actually carries, which is usually the whole
     * explanation.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RestErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isAnonymous(authentication)) {
            return respond(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required for this endpoint.",
                    List.of(new FieldResponse("Authorization", hasBearerToken(request)
                            ? "The bearer token was rejected."
                            : "No bearer token was sent.")),
                    request,
                    exception
            );
        }
        return respond(
                HttpStatus.FORBIDDEN,
                "Your account is not allowed to perform this action.",
                List.of(new FieldResponse("grantedAuthorities", authorities(authentication))),
                request,
                exception
        );
    }

    /**
     * A rejected or expired token. OAuth2 puts the real reason ("Jwt expired
     * at ...") in the error description, which is far more actionable than a
     * bare 401.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<RestErrorResponse> handleAuthenticationException(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        String reason = exception instanceof OAuth2AuthenticationException oauth2
                && oauth2.getError().getDescription() != null
                ? oauth2.getError().getDescription()
                : exception.getMessage();
        return respond(
                HttpStatus.UNAUTHORIZED,
                "Authentication failed.",
                List.of(new FieldResponse("Authorization", reason)),
                request,
                exception
        );
    }

    // --------------------------------------------------------------- fallback

    /**
     * Last resort. The caller gets a stable body and a trace id; the full stack
     * trace goes to the log under that same id.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong while handling the request.",
                null,
                request,
                exception
        );
    }

    // ---------------------------------------------------------------- internals

    private ResponseEntity<RestErrorResponse> respond(
            HttpStatus status,
            String message,
            Object details,
            HttpServletRequest request,
            Exception exception
    ) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        logFailure(status, traceId, request, exception);
        return ResponseEntity.status(status).body(RestErrorResponse.builder()
                .message(message)
                .code(status.value())
                .status(status.getReasonPhrase())
                .timestamp(Instant.now())
                .path(request == null ? null : request.getRequestURI())
                .method(request == null ? null : request.getMethod())
                .traceId(traceId)
                .exception(includeExceptionDetails && exception != null
                        ? exception.getClass().getName()
                        : null)
                .errorDetails(details != null ? details : describe(exception))
                .build());
    }

    /**
     * Server faults are logged with their stack trace because nobody else will
     * see it; client faults are logged at debug so a caller cannot flood the log
     * by sending bad requests on purpose.
     */
    private void logFailure(HttpStatus status, String traceId, HttpServletRequest request, Exception exception) {
        String method = request == null ? "-" : request.getMethod();
        String path = request == null ? "-" : request.getRequestURI();
        if (status.is5xxServerError()) {
            log.error("[{}] {} {} failed with {}", traceId, method, path, status.value(), exception);
        } else if (log.isDebugEnabled()) {
            log.debug("[{}] {} {} rejected with {}: {}", traceId, method, path, status.value(),
                    exception == null ? "" : exception.getMessage());
        }
    }

    private ExceptionDetailResponse describe(Exception exception) {
        if (!includeExceptionDetails || exception == null) {
            return null;
        }
        List<String> causes = new ArrayList<>();
        Throwable cause = exception.getCause();
        while (cause != null && causes.size() < 5) {
            causes.add(cause.getClass().getSimpleName() + ": " + firstLine(cause.getMessage()));
            cause = cause.getCause();
        }
        return new ExceptionDetailResponse(
                exception.getClass().getName(),
                firstLine(exception.getMessage()),
                originOf(exception),
                causes.isEmpty() ? null : causes
        );
    }

    /**
     * @return the first stack frame inside this application, which is where the
     * failure actually started rather than wherever the framework caught it.
     */
    private String originOf(Throwable throwable) {
        for (StackTraceElement frame : throwable.getStackTrace()) {
            if (frame.getClassName().startsWith(APP_PACKAGE)) {
                return frame.toString();
            }
        }
        return null;
    }

    private FieldResponse toFieldResponse(ObjectError error) {
        return error instanceof FieldError fieldError
                ? new FieldResponse(fieldError.getField(), fieldError.getDefaultMessage(), fieldError.getRejectedValue())
                : new FieldResponse(error.getObjectName(), error.getDefaultMessage());
    }

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    private boolean hasBearerToken(HttpServletRequest request) {
        String header = request == null ? null : request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ");
    }

    private String authorities(Authentication authentication) {
        List<String> granted = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return granted.isEmpty() ? "none" : String.join(", ", granted);
    }

    /**
     * Driver and parser messages often run to several lines of SQL or JSON
     * context; the first line is the part a caller can act on.
     */
    private String firstLine(String message) {
        if (message == null) {
            return null;
        }
        int lineBreak = message.indexOf('\n');
        String line = lineBreak < 0 ? message : message.substring(0, lineBreak);
        return line.strip();
    }

    private String describeType(Class<?> type) {
        if (!type.isEnum()) {
            return "a value of type " + type.getSimpleName();
        }
        return "one of " + Arrays.stream(type.getEnumConstants()).map(String::valueOf).toList();
    }

    private String[] nullSafe(String[] values) {
        return values == null ? new String[0] : values;
    }
}
