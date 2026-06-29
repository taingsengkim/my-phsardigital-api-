package co.istad.projectpracticum.phsardigital.core.event;
import java.util.UUID;

public record FileDeletedEvent(
        UUID fileId,
        String objectName
) {}