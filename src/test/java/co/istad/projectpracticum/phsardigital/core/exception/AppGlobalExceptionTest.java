package co.istad.projectpracticum.phsardigital.core.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class AppGlobalExceptionTest {

    private final AppGlobalException handler = new AppGlobalException();

    @Test
    void responseStatusExceptionPreservesHttpStatusAndReason() {
        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists.")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getBody().message()).isEqualTo("Username or email already exists.");
    }
}
