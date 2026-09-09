package co.istad.projectpracticum.phsardigital.features.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param body             the typed text. Optional since voice notes arrived — but a
 *                         message still cannot be empty, see {@link #hasContent()}. Sent
 *                         alongside a recording it reads as a caption.
 * @param voiceObjectName  the recording, already uploaded through
 *                         {@code POST /api/v1/files/voice}. It must be a file this caller
 *                         uploaded; naming somebody else's is refused.
 * @param voiceDurationSeconds how long that recording runs, as the recorder measured it.
 *                         A display hint the server cannot verify, so it is bounded
 *                         rather than trusted.
 * @param listingUuid      the product the sender is writing about, when they opened the
 *                         chat from a listing page. Optional, and normally sent only on
 *                         the first message of an enquiry. The listing must belong to one
 *                         of the two people in the conversation.
 */
public record SendMessageRequest(

        @Size(max = 5000, message = "Message must not exceed 5000 characters")
        String body,

        String voiceObjectName,

        @Min(value = 1, message = "A voice message must be at least a second long")
        @Max(value = 300, message = "A voice message must not exceed 5 minutes")
        Integer voiceDurationSeconds,

        UUID listingUuid
) {

    /**
     * A message has to actually say something.
     *
     * <p>Replaces the {@code @NotBlank} that used to sit on {@code body}: text alone is
     * still fine, a recording alone is now fine, and neither is not. Checked here rather
     * than in the service so an empty send is refused at the edge with the field named,
     * like every other validation failure in this API.
     *
     * <p>Named {@code isX} deliberately. Bean Validation only picks a constraint up off a
     * JavaBeans getter, so a more natural {@code hasContent()} is silently ignored — the
     * rule reads as if it applies and enforces nothing.
     */
    @JsonIgnore
    @AssertTrue(message = "A message needs either text or a voice recording")
    public boolean isContentPresent() {
        return (body != null && !body.isBlank())
                || (voiceObjectName != null && !voiceObjectName.isBlank());
    }

    /**
     * A duration is meaningless without a recording, and its absence alongside one leaves
     * the client drawing a bubble it cannot size.
     */
    @JsonIgnore
    @AssertTrue(message = "voiceDurationSeconds is required with a voice recording, and only with one")
    public boolean isDurationPairedWithVoice() {
        boolean hasVoice = voiceObjectName != null && !voiceObjectName.isBlank();
        return hasVoice == (voiceDurationSeconds != null);
    }
}
