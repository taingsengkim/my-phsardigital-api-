package co.istad.projectpracticum.phsardigital.core.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts the JSON a client actually receives, which the handler-level tests
 * cannot: the shared envelope, and that every failure the framework raises
 * before or inside a controller comes back in that same envelope.
 */
class AppGlobalExceptionWebTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AppGlobalException advice = new AppGlobalException();
        ReflectionTestUtils.setField(advice, "includeExceptionDetails", true);
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(advice)
                .build();
    }

    @Test
    void rejectedFieldsAreNamedAlongsideTheValueThatWasSent() throws Exception {
        mockMvc.perform(post("/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.status").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/stub"))
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.errorDetails[?(@.field == 'title')]").exists())
                .andExpect(jsonPath("$.errorDetails[?(@.field == 'quantity')].rejectedValue").value(0));
    }

    @Test
    void malformedJsonSaysWhatCouldNotBeParsed() throws Exception {
        mockMvc.perform(post("/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or not readable."))
                .andExpect(jsonPath("$.errorDetails[0].field").value("body"))
                .andExpect(jsonPath("$.errorDetails[0].fieldMessage").isNotEmpty());
    }

    @Test
    void anUnconvertiblePathVariableNamesTheParameter() throws Exception {
        mockMvc.perform(get("/stub/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetails[0].field").value("id"))
                .andExpect(jsonPath("$.errorDetails[0].rejectedValue").value("not-a-number"));
    }

    @Test
    void aMissingParameterIsReportedByName() throws Exception {
        mockMvc.perform(get("/stub"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetails[0].field").value("q"));
    }

    @Test
    void anUnhandledFailureStillReturnsTheSharedEnvelope() throws Exception {
        mockMvc.perform(get("/stub/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong while handling the request."))
                .andExpect(jsonPath("$.exception").value(IllegalStateException.class.getName()))
                .andExpect(jsonPath("$.errorDetails.message").value("kaboom"))
                .andExpect(jsonPath("$.errorDetails.origin").value(
                        org.hamcrest.Matchers.containsString("StubController")));
    }

    @RestController
    static class StubController {

        record Payload(@NotBlank String title, @Min(1) int quantity) {
        }

        @PostMapping("/stub")
        String create(@Valid @RequestBody Payload payload) {
            return payload.title();
        }

        @GetMapping("/stub/{id}")
        String byId(@PathVariable int id) {
            return String.valueOf(id);
        }

        @GetMapping("/stub")
        String search(@RequestParam String q) {
            return q;
        }

        @GetMapping("/stub/boom")
        String boom() {
            throw new IllegalStateException("kaboom");
        }
    }
}
