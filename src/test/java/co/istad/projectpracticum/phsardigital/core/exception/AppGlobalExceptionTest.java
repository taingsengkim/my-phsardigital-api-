package co.istad.projectpracticum.phsardigital.core.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AppGlobalExceptionTest {

    private final AppGlobalException handler = new AppGlobalException();

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/listings");

    @Test
    void responseStatusExceptionPreservesHttpStatusAndReason() {
        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists."),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getBody().message()).isEqualTo("Username or email already exists.");
    }

    @Test
    void everyErrorCarriesTheFailingRequestAndATraceId() {
        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found."),
                request
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path()).isEqualTo("/api/v1/listings");
        assertThat(response.getBody().method()).isEqualTo("POST");
        assertThat(response.getBody().traceId()).isNotBlank();
    }

    @Test
    void validationErrorsReportTheFieldAndTheRejectedValue() {
        var target = new Object();
        var binding = new BeanPropertyBindingResult(target, "createListingRequest");
        binding.rejectValue(null, "price", "must be greater than 0");
        binding.addError(new org.springframework.validation.FieldError(
                "createListingRequest", "price", -5, false, null, null, "must be greater than 0"));

        var response = handler.handleValidationException(new BindException(binding), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        List<FieldResponse> fields = (List<FieldResponse>) response.getBody().errorDetails();
        assertThat(fields)
                .extracting(FieldResponse::field, FieldResponse::rejectedValue)
                .contains(org.assertj.core.api.Assertions.tuple("price", -5));
    }

    @Test
    void typeMismatchNamesTheParameterAndTheValueSent() {
        var exception = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "listingId", null, new IllegalArgumentException("nope"));

        var response = handler.handleTypeMismatch(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("abc").contains("listingId");
    }

    @Test
    void concurrentVersionConflictAsksTheClientToReload() {
        var response = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException("Category", UUID.randomUUID()),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Reload").contains("try again");
    }

    @Test
    void rowLockConflictAsksTheClientToRetry() {
        var response = handler.handlePessimisticLockingFailure(
                new CannotAcquireLockException("lock timeout"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("another request").contains("Try again");
    }

    @Test
    void databaseConstraintErrorsNeverExposeSchemaDetails() {
        String privateDetail = "duplicate key violates constraint users_email_key (email=test@example.com)";
        ReflectionTestUtils.setField(handler, "includeExceptionDetails", true);

        var response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("insert failed", new RuntimeException(privateDetail)),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).doesNotContain(privateDetail)
                .doesNotContain("users_email_key")
                .doesNotContain("test@example.com");
    }

    @Test
    void accessDeniedIsUnauthorizedForAnAnonymousCaller() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        try {
            var response = handler.handleAccessDenied(new AccessDeniedException("denied"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void accessDeniedIsForbiddenAndListsTheAuthoritiesTheCallerHas() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "sengkim", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            var response = handler.handleAccessDenied(new AccessDeniedException("denied"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            @SuppressWarnings("unchecked")
            List<FieldResponse> fields = (List<FieldResponse>) response.getBody().errorDetails();
            assertThat(fields).singleElement()
                    .extracting(FieldResponse::fieldMessage)
                    .isEqualTo("ROLE_USER");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void unexpectedFailuresExposeTheCauseChainOnlyWhenDetailsAreEnabled() {
        var boom = new IllegalStateException("connection pool exhausted", new RuntimeException("timeout"));

        var hidden = handler.handleUnexpectedException(boom, request);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(hidden.getBody()).isNotNull();
        assertThat(hidden.getBody().errorDetails()).isNull();
        assertThat(hidden.getBody().exception()).isNull();

        ReflectionTestUtils.setField(handler, "includeExceptionDetails", true);
        var shown = handler.handleUnexpectedException(boom, request);
        assertThat(shown.getBody()).isNotNull();
        assertThat(shown.getBody().exception()).isEqualTo(IllegalStateException.class.getName());
        assertThat(shown.getBody().errorDetails())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ExceptionDetailResponse.class))
                .extracting(ExceptionDetailResponse::message, ExceptionDetailResponse::causes)
                .containsExactly("connection pool exhausted", List.of("RuntimeException: timeout"));
    }
}
