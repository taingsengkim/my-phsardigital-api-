package co.istad.projectpracticum.phsardigital.features.auth;

import co.istad.projectpracticum.phsardigital.config.security.KeycloakAdminProps;
import co.istad.projectpracticum.phsardigital.features.auth.dto.RegisterRequest;
import co.istad.projectpracticum.phsardigital.features.auth.dto.RegisterResponse;
import co.istad.projectpracticum.phsardigital.features.seller.SellerRepository;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileMapper;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import co.istad.projectpracticum.phsardigital.features.user.UserProvisioningService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String REALM = "phsardigital";
    private static final String USER_ID = "created-user-id";

    @Mock
    private Keycloak keycloak;
    @Mock
    private RealmResource realmResource;
    @Mock
    private UsersResource usersResource;
    @Mock
    private UserResource userResource;
    @Mock
    private RolesResource rolesResource;
    @Mock
    private RoleResource roleResource;
    @Mock
    private RoleMappingResource roleMappingResource;
    @Mock
    private RoleScopeResource roleScopeResource;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private KeycloakAdminProps props;
    @Mock
    private AuthMapper authMapper;
    @Mock
    private Response response;
    @Mock
    private UserProvisioningService userProvisioningService;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private SellerRepository sellerRepository;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(keycloak, userProfileRepository, props, authMapper,
                userProvisioningService, userProfileMapper, sellerRepository);
        when(props.getTargetRealm()).thenReturn(REALM);
        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    @Test
    void registerCreatesIdentityRoleAndProfile() {
        RegisterRequest request = validRequest();
        RegisterResponse expected = expectedResponse();
        prepareSuccessfulKeycloakCreation();
        when(userProfileRepository.saveAndFlush(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authMapper.toRegisterResponse(eq(request), any(UserRepresentation.class)))
                .thenReturn(expected);

        RegisterResponse result = service.register(request);

        assertThat(result).isEqualTo(expected);
        verify(roleScopeResource).add(any());
        verify(userProfileRepository).saveAndFlush(any(UserProfile.class));
        verify(userResource).sendVerifyEmail();
        verify(userResource, never()).remove();
    }

    @Test
    void registerReturnsConflictWhenKeycloakRejectsDuplicate() {
        RegisterRequest request = validRequest();
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(HttpStatus.CONFLICT.value());

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());
                    assertThat(exception.getReason()).isEqualTo("Username or email already exists.");
                });

        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerRemovesKeycloakUserWhenProfileCreationFails() {
        prepareSuccessfulKeycloakCreation();
        when(userProfileRepository.saveAndFlush(any(UserProfile.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate profile"));

        assertThatThrownBy(() -> service.register(validRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode().value())
                                .isEqualTo(HttpStatus.BAD_GATEWAY.value()));

        verify(userResource).remove();
    }

    @Test
    void registerSucceedsWhenVerificationEmailIsTemporarilyUnavailable() {
        RegisterRequest request = validRequest();
        RegisterResponse expected = expectedResponse();
        prepareSuccessfulKeycloakCreation();
        when(userProfileRepository.saveAndFlush(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authMapper.toRegisterResponse(eq(request), any(UserRepresentation.class)))
                .thenReturn(expected);
        doThrow(new RuntimeException("mail unavailable")).when(userResource).sendVerifyEmail();

        RegisterResponse result = service.register(request);

        assertThat(result).isEqualTo(expected);
        verify(userResource, never()).remove();
    }

    private void prepareSuccessfulKeycloakCreation() {
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(HttpStatus.CREATED.value());
        when(response.getStatusInfo()).thenReturn(Response.Status.CREATED);
        when(response.getLocation()).thenReturn(
                URI.create("http://keycloak/admin/realms/phsardigital/users/" + USER_ID));
        when(usersResource.get(USER_ID)).thenReturn(userResource);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get(RoleEnum.USER.name())).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation());
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
    }

    private RegisterRequest validRequest() {
        return new RegisterRequest(
                "sokha.user",
                "correct horse battery staple",
                "correct horse battery staple",
                "sokha@example.com",
                "Sokha",
                "Chan",
                "012345678"
        );
    }

    private RegisterResponse expectedResponse() {
        return new RegisterResponse(
                USER_ID,
                "sokha.user",
                "sokha@example.com",
                "Sokha",
                "Chan",
                "012345678"
        );
    }
}
