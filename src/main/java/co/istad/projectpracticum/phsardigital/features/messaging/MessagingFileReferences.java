package co.istad.projectpracticum.phsardigital.features.messaging;

import co.istad.projectpracticum.phsardigital.features.file.FileReferenceCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A recording somebody has already sent as a voice message. */
@Component
@RequiredArgsConstructor
public class MessagingFileReferences implements FileReferenceCheck {

    private final MessageRepository messageRepository;

    @Override
    public String referenceName() {
        return "a voice message";
    }

    @Override
    public boolean isReferenced(String objectName) {
        return messageRepository.existsByVoiceFile_ObjectName(objectName);
    }

    /**
     * Refused rather than detached, which is different from how the other attachments in
     * this API behave.
     *
     * <p>Detaching is right for a listing thumbnail or a delivery landmark: the thing
     * loses a picture and survives. Here the recording <em>is</em> the message. Detaching
     * would leave the recipient an empty bubble where a voice note used to be — the
     * sender would have silently unsent something already delivered, and the thread would
     * no longer record what was said.
     *
     * <p>The consequence is that a sender cannot delete a recording once it is sent. That
     * is the correct default for a conversation, and the right way to lift it is a
     * delete-message feature that removes the message and its audio together — which does
     * not exist yet.
     */
    @Override
    public boolean isMandatory() {
        return true;
    }
}
