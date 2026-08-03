package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.user.dto.AdminUserResponse;
import co.istad.projectpracticum.phsardigital.features.user.dto.UserProfileResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class UserProfileMapper {
    public UserProfileResponse toResponse(UserProfile p) {
        return new UserProfileResponse(
                p.getId(),
                p.getEmail(),
                p.getFullName(),
                p.getPhone(),
                p.getAvatarUrl(),
                p.getStatus() != null ? p.getStatus().name() : null,
                p.getDateOfBirth()
        );
    }

    public AdminUserResponse toAdminResponse(UserProfile profile) {
        return new AdminUserResponse(
                profile.getId(),
                profile.getEmail(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getAvatarUrl(),
                profile.getStatus(),
                profile.getDateOfBirth(),
                profile.getCreatedAt(),
                profile.getLastModifiedAt()
        );
    }
}
