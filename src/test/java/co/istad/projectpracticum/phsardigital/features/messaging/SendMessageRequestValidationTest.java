package co.istad.projectpracticum.phsardigital.features.messaging;

import co.istad.projectpracticum.phsardigital.features.messaging.dto.SendMessageRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code body} lost its {@code @NotBlank} when voice notes arrived, so these cover what
 * replaced it: a message still cannot be empty, it just has two ways of not being.
 */
class SendMessageRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("text alone is still a message")
    void textOnly() {
        assertThat(validate(new SendMessageRequest("Is this still available?", null, null, null)))
                .isEmpty();
    }

    @Test
    @DisplayName("a recording alone is a message")
    void voiceOnly() {
        assertThat(validate(new SendMessageRequest(null, "voice/abc.webm", 12, null))).isEmpty();
    }

    @Test
    @DisplayName("a recording with a caption is a message")
    void voiceWithCaption() {
        assertThat(validate(new SendMessageRequest("about the red one", "voice/abc.webm", 12, null)))
                .isEmpty();
    }

    @Test
    @DisplayName("neither is not a message")
    void rejectsEmpty() {
        assertThat(paths(validate(new SendMessageRequest(null, null, null, null))))
                .contains("contentPresent");
    }

    @Test
    @DisplayName("whitespace is not text")
    void rejectsWhitespaceOnly() {
        assertThat(paths(validate(new SendMessageRequest("   ", null, null, null))))
                .contains("contentPresent");
    }

    @Test
    @DisplayName("a recording without a duration leaves the client unable to size the bubble")
    void rejectsVoiceWithoutDuration() {
        assertThat(paths(validate(new SendMessageRequest(null, "voice/abc.webm", null, null))))
                .contains("durationPairedWithVoice");
    }

    @Test
    @DisplayName("a duration without a recording describes nothing")
    void rejectsDurationWithoutVoice() {
        assertThat(paths(validate(new SendMessageRequest("hello", null, 12, null))))
                .contains("durationPairedWithVoice");
    }

    @Test
    @DisplayName("a recording has to be bounded at both ends")
    void rejectsImpossibleDurations() {
        assertThat(paths(validate(new SendMessageRequest(null, "voice/abc.webm", 0, null))))
                .contains("voiceDurationSeconds");
        assertThat(paths(validate(new SendMessageRequest(null, "voice/abc.webm", 301, null))))
                .contains("voiceDurationSeconds");
    }

    @Test
    @DisplayName("caps the caption so a bubble cannot carry an essay")
    void rejectsOverlongBody() {
        assertThat(paths(validate(new SendMessageRequest("x".repeat(5001), null, null, null))))
                .contains("body");
    }

    private static Set<ConstraintViolation<SendMessageRequest>> validate(SendMessageRequest request) {
        return validator.validate(request);
    }

    private static Set<String> paths(Set<ConstraintViolation<SendMessageRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
