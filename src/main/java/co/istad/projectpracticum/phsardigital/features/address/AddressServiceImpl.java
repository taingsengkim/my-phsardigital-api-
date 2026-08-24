package co.istad.projectpracticum.phsardigital.features.address;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.address.dto.AddressPhotoRequest;
import co.istad.projectpracticum.phsardigital.features.address.dto.AddressPhotoResponse;
import co.istad.projectpracticum.phsardigital.features.address.dto.AddressRequest;
import co.istad.projectpracticum.phsardigital.features.address.dto.AddressResponse;
import co.istad.projectpracticum.phsardigital.features.address.dto.UpdateAddressRequest;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserProfileRepository userProfileRepository;
    private final FileUploadService fileUploadService;

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses() {
        return addressRepository
                .findByUserProfile_IdOrderByIsDefaultDescCreatedAtDesc(AuthUtils.extractUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse create(AddressRequest request) {
        String userId = AuthUtils.extractUserId();
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Address address = new Address();
        address.setUserProfile(user);
        address.setLabel(request.label());
        address.setRecipient(request.recipient());
        address.setPhone(request.phone());
        address.setLine1(request.line1());
        address.setLine2(request.line2());
        address.setCity(request.city());
        address.setProvince(request.province());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
        replacePhotos(address, request.landmarkPhotos(), userId);

        // The first address saved becomes the default whatever the request says, so a
        // user who never thinks about it still has one for checkout to pick.
        boolean first = addressRepository.countByUserProfile_Id(userId) == 0;
        address.setIsDefault(first || Boolean.TRUE.equals(request.isDefault()));

        Address saved = addressRepository.save(address);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            addressRepository.clearOtherDefaults(userId, saved.getId());
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AddressResponse update(UUID id, UpdateAddressRequest request) {
        String userId = AuthUtils.extractUserId();
        Address address = requireOwned(id, userId);

        if (request.label() != null) {
            address.setLabel(request.label());
        }
        if (request.recipient() != null) {
            address.setRecipient(request.recipient());
        }
        if (request.phone() != null) {
            address.setPhone(request.phone());
        }
        if (request.line1() != null) {
            address.setLine1(request.line1());
        }
        if (request.line2() != null) {
            address.setLine2(request.line2());
        }
        if (request.city() != null) {
            address.setCity(request.city());
        }
        if (request.province() != null) {
            address.setProvince(request.province());
        }
        if (request.latitude() != null) {
            address.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            address.setLongitude(request.longitude());
        }
        // Absent leaves the photos alone; present replaces the set, so a PATCH is also
        // how one gets removed.
        if (request.landmarkPhotos() != null) {
            replacePhotos(address, request.landmarkPhotos(), userId);
        }

        // Only a promotion, never a demotion — see UpdateAddressRequest.isDefault.
        boolean promoting = Boolean.TRUE.equals(request.isDefault());
        if (promoting) {
            address.setIsDefault(true);
        }

        Address saved = addressRepository.save(address);

        // After the save, as in create(): this row has to hold the flag before the
        // others lose it, or a failure in between leaves the account with none.
        if (promoting) {
            addressRepository.clearOtherDefaults(userId, id);
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AddressResponse makeDefault(UUID id) {
        String userId = AuthUtils.extractUserId();
        Address address = requireOwned(id, userId);

        address.setIsDefault(true);
        Address saved = addressRepository.save(address);
        addressRepository.clearOtherDefaults(userId, id);
        return toResponse(saved);
    }

    /**
     * Deletes outright. Safe because a purchase copies the address text rather than
     * pointing at the row — see {@code PurchaseServiceImpl.checkout} — so removing a
     * saved address never rewrites where a past order went.
     */
    @Override
    @Transactional
    public void delete(UUID id) {
        String userId = AuthUtils.extractUserId();
        Address address = requireOwned(id, userId);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());

        addressRepository.delete(address);

        // Promote another one, so deleting the default does not leave the account with
        // addresses but nothing for checkout to pick.
        if (wasDefault) {
            addressRepository.findByUserProfile_IdOrderByIsDefaultDescCreatedAtDesc(userId)
                    .stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setIsDefault(true);
                        addressRepository.save(next);
                    });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Address requireOwned(UUID id, String userId) {
        return addressRepository.findByIdAndUserProfile_Id(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    /**
     * Points the address at exactly the photos named, in the order given.
     *
     * <p>Every object name is resolved against this caller in one query before
     * anything is attached, so a name belonging to somebody else fails the request
     * outright rather than half-applying it. The existing rows are cleared and rebuilt
     * rather than diffed: at three photos the comparison would cost more than the
     * writes it saves, and orphan removal takes care of what drops out.
     */
    private void replacePhotos(Address address, List<AddressPhotoRequest> requested, String ownerId) {
        address.getPhotos().clear();
        if (requested == null || requested.isEmpty()) {
            return;
        }

        // Deduplicated, because the same shot sent twice is one landmark, not two.
        Set<String> objectNames = requested.stream()
                .map(AddressPhotoRequest::objectName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, FileUpload> owned = fileUploadService
                .requireOwnedFiles(objectNames, ownerId)
                .stream()
                .collect(Collectors.toMap(FileUpload::getObjectName, Function.identity(), (a, b) -> a));

        List<AddressPhoto> rebuilt = new ArrayList<>();
        int sortOrder = 0;
        for (String objectName : objectNames) {
            AddressPhoto photo = new AddressPhoto();
            photo.setAddress(address);
            photo.setFile(owned.get(objectName));
            photo.setCaption(captionFor(requested, objectName));
            photo.setSortOrder(sortOrder++);
            rebuilt.add(photo);
        }
        address.getPhotos().addAll(rebuilt);
    }

    /** The first caption sent for this object name, matching the deduplication above. */
    private static String captionFor(List<AddressPhotoRequest> requested, String objectName) {
        return requested.stream()
                .filter(photo -> objectName.equals(photo.objectName()))
                .map(AddressPhotoRequest::caption)
                .filter(caption -> caption != null && !caption.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private AddressResponse toResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getRecipient(),
                address.getPhone(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getProvince(),
                address.getLatitude(),
                address.getLongitude(),
                address.getIsDefault(),
                toPhotoResponses(address));
    }

    private List<AddressPhotoResponse> toPhotoResponses(Address address) {
        return address.getPhotos().stream()
                .map(photo -> new AddressPhotoResponse(
                        fileUploadService.getPreviewUrl(photo.getFile()),
                        photo.getCaption()))
                .toList();
    }
}
