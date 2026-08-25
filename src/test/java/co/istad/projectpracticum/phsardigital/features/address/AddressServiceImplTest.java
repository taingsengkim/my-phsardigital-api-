package co.istad.projectpracticum.phsardigital.features.address;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.address.dto.AddressPhotoRequest;
import co.istad.projectpracticum.phsardigital.features.address.dto.AddressRequest;
import co.istad.projectpracticum.phsardigital.features.address.dto.AddressResponse;
import co.istad.projectpracticum.phsardigital.features.address.dto.UpdateAddressRequest;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    private static final String USER_ID = "buyer-1";

    @Mock
    private AddressRepository addressRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private FileUploadService fileUploadService;
    @InjectMocks
    private AddressServiceImpl service;

    @Test
    void creatingAnAddressAttachesLandmarkPhotosInRequestOrder() {
        when(userProfileRepository.findById(USER_ID))
                .thenReturn(Optional.of(new UserProfile(USER_ID)));
        when(fileUploadService.requireOwnedFiles(any(), eq(USER_ID)))
                .thenReturn(List.of(file("road.jpg"), file("gate.jpg")));
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fileUploadService.getPreviewUrl(any(FileUpload.class)))
                .thenAnswer(invocation ->
                        "https://cdn/" + invocation.getArgument(0, FileUpload.class).getObjectName());

        AddressResponse response;
        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            response = service.create(cityRequest(List.of(
                    new AddressPhotoRequest("road.jpg", "  turn at the pagoda  "),
                    new AddressPhotoRequest("gate.jpg", "blue gate"))));
        }

        assertThat(response.landmarkPhotos()).hasSize(2);
        assertThat(response.landmarkPhotos().get(0).url()).isEqualTo("https://cdn/road.jpg");
        assertThat(response.landmarkPhotos().get(0).caption()).isEqualTo("turn at the pagoda");
        assertThat(response.landmarkPhotos().get(1).caption()).isEqualTo("blue gate");
        // Ownership is settled in one query before anything is attached.
        verify(fileUploadService).requireOwnedFiles(any(), eq(USER_ID));
    }

    @Test
    void theSamePhotoSentTwiceIsAttachedOnce() {
        when(userProfileRepository.findById(USER_ID))
                .thenReturn(Optional.of(new UserProfile(USER_ID)));
        when(fileUploadService.requireOwnedFiles(any(), eq(USER_ID)))
                .thenReturn(List.of(file("gate.jpg")));
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response;
        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            response = service.create(cityRequest(List.of(
                    new AddressPhotoRequest("gate.jpg", "blue gate"),
                    new AddressPhotoRequest("gate.jpg", "blue gate again"))));
        }

        assertThat(response.landmarkPhotos()).hasSize(1);
        assertThat(response.landmarkPhotos().getFirst().caption()).isEqualTo("blue gate");
    }

    @Test
    void aCityAddressReadsFromTheNamedPlaceOutToTheDistrict() {
        when(userProfileRepository.findById(USER_ID))
                .thenReturn(Optional.of(new UserProfile(USER_ID)));
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response;
        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            response = service.create(cityRequest(null));
        }

        assertThat(response.type()).isEqualTo(AddressType.CITY);
        assertThat(response.formattedAddress()).isEqualTo(
                "Borey Peng Huoth, 271, Phum 3, Tuol Tumpung Ti Muoy, Chamkar Mon");
        assertThat(response.province()).isNull();
    }

    @Test
    void aProvinceAddressReadsFromTheVillageOutToTheProvince() {
        when(userProfileRepository.findById(USER_ID))
                .thenReturn(Optional.of(new UserProfile(USER_ID)));
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response;
        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            response = service.create(provinceRequest());
        }

        assertThat(response.type()).isEqualTo(AddressType.PROVINCE);
        assertThat(response.formattedAddress()).isEqualTo("Phum Thmei, Satv Pong, Chhuk, Kampot");
        assertThat(response.locationName()).isNull();
        assertThat(response.streetNo()).isNull();
    }

    @Test
    void aProvinceAddressRefusesAFieldOnlyACityAddressHas() {
        when(userProfileRepository.findById(USER_ID))
                .thenReturn(Optional.of(new UserProfile(USER_ID)));

        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            assertThatThrownBy(() -> service.create(new AddressRequest(
                    AddressType.PROVINCE, "Home", "Dara", "012345678",
                    null, "271", "Kampot",
                    "Chhuk", "Satv Pong", "Phum Thmei",
                    null, null, null, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("A street no. does not belong to a PROVINCE address");
        }

        verify(addressRepository, never()).save(any(Address.class));
    }

    @Test
    void anAddressMissingPartOfItsChainIsRefused() {
        when(userProfileRepository.findById(USER_ID))
                .thenReturn(Optional.of(new UserProfile(USER_ID)));

        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            assertThatThrownBy(() -> service.create(new AddressRequest(
                    AddressType.PROVINCE, "Home", "Dara", "012345678",
                    null, null, "Kampot",
                    "Chhuk", "Satv Pong", "   ",
                    null, null, null, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("A village is required");
        }

        verify(addressRepository, never()).save(any(Address.class));
    }

    @Test
    void anAddressWithoutAShapeIsRefused() {
        when(userProfileRepository.findById(USER_ID))
                .thenReturn(Optional.of(new UserProfile(USER_ID)));

        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            assertThatThrownBy(() -> service.create(new AddressRequest(
                    null, "Home", "Dara", "012345678",
                    null, null, "Kampot",
                    "Chhuk", "Satv Pong", "Phum Thmei",
                    null, null, null, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("An address type is required");
        }

        verify(addressRepository, never()).save(any(Address.class));
    }

    @Test
    void switchingShapeInOnePatchDropsWhatTheOldShapeOwned() {
        Address existing = savedAddressWithOnePhoto();
        when(addressRepository.findByIdAndUserProfile_Id(existing.getId(), USER_ID))
                .thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        AddressResponse response;
        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            response = service.update(existing.getId(), new UpdateAddressRequest(
                    AddressType.PROVINCE, null, null, null,
                    null, null, "Kampot",
                    "Chhuk", "Satv Pong", "Phum Thmei",
                    null, null, null, null));
        }

        assertThat(response.type()).isEqualTo(AddressType.PROVINCE);
        assertThat(response.locationName()).isNull();
        assertThat(response.streetNo()).isNull();
        assertThat(response.formattedAddress()).isEqualTo("Phum Thmei, Satv Pong, Chhuk, Kampot");
    }

    @Test
    void aPatchThatWouldLeaveTheNewShapeIncompleteIsRefused() {
        Address existing = savedAddressWithOnePhoto();
        when(addressRepository.findByIdAndUserProfile_Id(existing.getId(), USER_ID))
                .thenReturn(Optional.of(existing));

        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            assertThatThrownBy(() -> service.update(existing.getId(), new UpdateAddressRequest(
                    AddressType.PROVINCE, null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("A province is required");
        }

        verify(addressRepository, never()).save(any(Address.class));
    }

    @Test
    void patchingWithoutPhotosLeavesTheExistingOnesAlone() {
        Address existing = savedAddressWithOnePhoto();
        when(addressRepository.findByIdAndUserProfile_Id(existing.getId(), USER_ID))
                .thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            service.update(existing.getId(), new UpdateAddressRequest(
                    null, "Home", null, null, null, null, null,
                    null, null, null, null, null, null, null));
        }

        assertThat(existing.getPhotos()).hasSize(1);
        verify(fileUploadService, never()).requireOwnedFiles(any(), any());
    }

    @Test
    void patchingWithAnEmptyListClearsThePhotos() {
        Address existing = savedAddressWithOnePhoto();
        when(addressRepository.findByIdAndUserProfile_Id(existing.getId(), USER_ID))
                .thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            service.update(existing.getId(), new UpdateAddressRequest(
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, List.of()));
        }

        assertThat(existing.getPhotos()).isEmpty();
    }

    private static MockedStatic<AuthUtils> authenticatedUser() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(USER_ID);
        return auth;
    }

    private static AddressRequest cityRequest(List<AddressPhotoRequest> photos) {
        return new AddressRequest(
                AddressType.CITY, "Home", "Dara", "012345678",
                "Borey Peng Huoth", "271", null,
                "Chamkar Mon", "Tuol Tumpung Ti Muoy", "Phum 3",
                null, null, null, photos);
    }

    private static AddressRequest provinceRequest() {
        return new AddressRequest(
                AddressType.PROVINCE, "Home", "Dara", "012345678",
                null, null, "Kampot",
                "Chhuk", "Satv Pong", "Phum Thmei",
                null, null, null, null);
    }

    private static Address savedAddressWithOnePhoto() {
        Address address = new Address();
        address.setId(UUID.randomUUID());
        address.setType(AddressType.CITY);
        address.setLocationName("Borey Peng Huoth");
        address.setStreetNo("271");
        address.setDistrict("Chamkar Mon");
        address.setCommune("Tuol Tumpung Ti Muoy");
        address.setVillage("Phum 3");
        address.setFormattedAddress(
                "Borey Peng Huoth, 271, Phum 3, Tuol Tumpung Ti Muoy, Chamkar Mon");

        AddressPhoto photo = new AddressPhoto();
        photo.setUuid(UUID.randomUUID());
        photo.setAddress(address);
        photo.setFile(file("gate.jpg"));
        photo.setCaption("blue gate");
        photo.setSortOrder(0);
        address.getPhotos().add(photo);
        return address;
    }

    private static FileUpload file(String objectName) {
        FileUpload file = new FileUpload();
        file.setId(UUID.randomUUID());
        file.setObjectName(objectName);
        return file;
    }
}
