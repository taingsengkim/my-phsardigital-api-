package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.user.dto.AdminUserResponse;
import co.istad.projectpracticum.phsardigital.features.user.dto.UserProfileResponse;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class UserProfileMapper {

    @Autowired
    protected FileUploadService fileUploadService;

    public UserProfileResponse toResponse(UserProfile p) {
        return new UserProfileResponse(
                p.getId(),
                p.getUsername(),
                p.getEmail(),
                p.getEmailVerified(),
                p.getFirstName(),
                p.getLastName(),
                p.getFullName(),
                p.getPhone(),
                avatarUrl(p.getAvatarFile()),
                p.getAvatarFile() != null ? p.getAvatarFile().getObjectName() : null,
                p.getGender(),
                p.getBio(),
                p.getDateOfBirth(),
                p.getStatus() != null ? p.getStatus().name() : null,
                p.getCreatedAt(),
                p.getLastModifiedAt()
        );
    }

    public AdminUserResponse toAdminResponse(UserProfile profile) {
        return new AdminUserResponse(
                profile.getId(),
                profile.getUsername(),
                profile.getEmail(),
                profile.getEmailVerified(),
                profile.getFullName(),
                profile.getPhone(),
                avatarUrl(profile.getAvatarFile()),
                profile.getGender(),
                profile.getStatus(),
                profile.getDateOfBirth(),
                profile.getCreatedAt(),
                profile.getLastModifiedAt()
        );
    }

    private String avatarUrl(FileUpload avatar) {
        return fileUploadService.getPreviewUrl(avatar);
    }
}
