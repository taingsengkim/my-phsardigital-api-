package co.istad.sengkim.phsardigital.core.event;
import java.util.UUID;

public record FileDeletedEvent(
        UUID fileId,
        String objectName
) {}