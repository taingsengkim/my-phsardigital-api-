package co.istad.projectpracticum.phsardigital.features.user.listener;

import co.istad.projectpracticum.phsardigital.core.event.FileDeletedEvent;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detaches avatars whose underlying file is being deleted, so removing a file
 * never trips the foreign key on {@code users.avatar_file_id}.
 */
@Component
@RequiredArgsConstructor
public class UserProfileFileListener {

    private final UserProfileRepository userProfileRepository;

    @EventListener
    @Transactional
    public void handle(FileDeletedEvent event) {
        List<UserProfile> profiles = userProfileRepository
                .findAllByAvatarFile_ObjectName(event.objectName());
        for (UserProfile profile : profiles) {
            profile.setAvatarFile(null);
        }
    }
}
