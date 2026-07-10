package co.istad.projectpracticum.phsardigital.features.auth;

import co.istad.projectpracticum.phsardigital.config.security.KeycloakAdminProps;
import co.istad.projectpracticum.phsardigital.features.auth.dto.RegisterRequest;
import co.istad.projectpracticum.phsardigital.features.auth.dto.RegisterResponse;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import co.istad.projectpracticum.phsardigital.features.user.UserStatus;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService{
    private final Keycloak keycloak;
    private final UserProfileRepository userProfileRepository;
    private final KeycloakAdminProps props;
    private final AuthMapper authMapper;


    @Override
    public RegisterResponse register(RegisterRequest request) {
        if(!request.password().equals(request.confirmPassword())){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password doesn't match!");
        }
        UsersResource userResource = keycloak.realm(props.getTargetRealm()).users();
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(request.username());
        userRepresentation.setEmail(request.email());
        userRepresentation.setFirstName(request.firstName());
        userRepresentation.setLastName(request.lastName());

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());

        userRepresentation.setEnabled(true);
        userRepresentation.setEmailVerified(false);
        userRepresentation.setCredentials(List.of(credential));
        try(Response response = userResource.create(userRepresentation)) {
            log.info("Response status code : {}" , response.getStatus());
            if(response.getStatus() == HttpStatus.CREATED.value()){
                UserRepresentation createdUser = keycloak.realm(props.getTargetRealm()).users()
                        .search(userRepresentation.getUsername())
                        .getFirst();

                UserResource userResourceSet = keycloak.realm(props.getTargetRealm())
                        .users().get(createdUser.getId());
                userResourceSet.sendVerifyEmail();

                try {
                    RoleRepresentation roleUser = keycloak.realm(props.getTargetRealm())
                            .roles().get(RoleEnum.USER.name()).toRepresentation();
                    userResourceSet.roles().realmLevel().add(List.of(roleUser));
                } catch (Exception e) {
                    log.error("Role assignment failed: {}", e.getMessage(), e);
                    throw e;
                }

                UserProfile userProfile = new UserProfile();
                userProfile.setId(createdUser.getId());
                userProfile.setEmail(request.email());
                userProfile.setFullName(request.firstName() + " " + request.lastName());
                userProfile.setStatus(UserStatus.ACTIVE);
                userProfile.setPhone(request.phoneNumber());
                userProfileRepository.save(userProfile);

                return authMapper.toRegisterResponse(request,createdUser);
            }else if (response.getStatus() == HttpStatus.CONFLICT.value()){
                log.info("Check username or email already exist");
            }
        }

        return null;
    }

}
