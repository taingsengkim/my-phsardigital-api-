package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.user.dto.UserProfileResponse;
import org.keycloak.representations.idm.UserRepresentation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
}
