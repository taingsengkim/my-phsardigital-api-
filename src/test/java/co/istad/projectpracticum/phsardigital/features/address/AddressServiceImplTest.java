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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
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
            response = service.create(request(List.of(
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
            response = service.create(request(List.of(
                    new AddressPhotoRequest("gate.jpg", "blue gate"),
                    new AddressPhotoRequest("gate.jpg", "blue gate again"))));
        }

        assertThat(response.landmarkPhotos()).hasSize(1);
        assertThat(response.landmarkPhotos().getFirst().caption()).isEqualTo("blue gate");
    }

    @Test
    void patchingWithoutPhotosLeavesTheExistingOnesAlone() {
        Address existing = savedAddressWithOnePhoto();
        when(addressRepository.findByIdAndUserProfile_Id(existing.getId(), USER_ID))
                .thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            service.update(existing.getId(), new UpdateAddressRequest(
                    "Home", null, null, null, null, null, null, null, null, null, null));
        }

        assertThat(existing.getPhotos()).hasSize(1);
        verify(fileUploadService, org.mockito.Mockito.never())
                .requireOwnedFiles(any(), any());
    }

    @Test
    void patchingWithAnEmptyListClearsThePhotos() {
        Address existing = savedAddressWithOnePhoto();
        when(addressRepository.findByIdAndUserProfile_Id(existing.getId(), USER_ID))
                .thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        try (MockedStatic<AuthUtils> auth = authenticatedUser()) {
            service.update(existing.getId(), new UpdateAddressRequest(
                    null, null, null, null, null, null, null, null, null, null, List.of()));
        }

        assertThat(existing.getPhotos()).isEmpty();
    }

    private static MockedStatic<AuthUtils> authenticatedUser() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(USER_ID);
        return auth;
    }

    private static AddressRequest request(List<AddressPhotoRequest> photos) {
        return new AddressRequest(
                "Home", "Dara", "012345678", "St 271", null,
                "Phnom Penh", "Phnom Penh", null, null, null, photos);
    }

    private static Address savedAddressWithOnePhoto() {
        Address address = new Address();
        address.setId(UUID.randomUUID());
        address.setLine1("St 271");

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
