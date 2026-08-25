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
import java.util.stream.Stream;

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

        rejectForeignFields(request.type(), request.province(),
                request.locationName(), request.streetNo());

        Address address = new Address();
        address.setUserProfile(user);
        address.setType(request.type());
        address.setLabel(request.label());
        address.setRecipient(request.recipient());
        address.setPhone(request.phone());
        address.setLocationName(trimToNull(request.locationName()));
        address.setStreetNo(trimToNull(request.streetNo()));
        address.setProvince(trimToNull(request.province()));
        address.setDistrict(trimToNull(request.district()));
        address.setCommune(trimToNull(request.commune()));
        address.setVillage(trimToNull(request.village()));
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
        settleShape(address);
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

        // Checked against the shape this address ends up in, not the one it is leaving,
        // so a PATCH that switches shape and fills the new one in a single call passes.
        AddressType shape = request.type() != null ? request.type() : address.getType();
        rejectForeignFields(shape, request.province(),
                request.locationName(), request.streetNo());

        if (request.type() != null) {
            address.setType(request.type());
        }
        if (request.label() != null) {
            address.setLabel(request.label());
        }
        if (request.recipient() != null) {
            address.setRecipient(request.recipient());
        }
        if (request.phone() != null) {
            address.setPhone(request.phone());
        }
        if (request.locationName() != null) {
            address.setLocationName(trimToNull(request.locationName()));
        }
        if (request.streetNo() != null) {
            address.setStreetNo(trimToNull(request.streetNo()));
        }
        if (request.province() != null) {
            address.setProvince(trimToNull(request.province()));
        }
        if (request.district() != null) {
            address.setDistrict(trimToNull(request.district()));
        }
        if (request.commune() != null) {
            address.setCommune(trimToNull(request.commune()));
        }
        if (request.village() != null) {
            address.setVillage(trimToNull(request.village()));
        }
        if (request.latitude() != null) {
            address.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            address.setLongitude(request.longitude());
        }
        settleShape(address);
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
     * Settles the shape once every field sent has been applied: drops what the other
     * shape owns, checks what this one needs is there, and renders the line an order
     * copies at checkout.
     *
     * <p>Runs on the merged address rather than on the request, because under a PATCH
     * only the merged state says whether the address is complete — the request may name
     * one field and leave the rest to what is already stored.
     */
    private static void settleShape(Address address) {
        clearForeignFields(address);
        requireShapeFields(address);
        address.setFormattedAddress(render(address));
    }

    /**
     * Refuses a field belonging to the shape this address is not.
     *
     * <p>Refused rather than dropped: a street no. arriving on a province address means
     * the caller filled a form for the other shape, and silently keeping the half that
     * happens to fit would store an address nobody can deliver to.
     */
    private static void rejectForeignFields(AddressType shape, String province,
                                            String locationName, String streetNo) {
        if (shape == AddressType.PROVINCE) {
            rejectField(locationName, "A location name", shape);
            rejectField(streetNo, "A street no.", shape);
        } else if (shape == AddressType.CITY) {
            rejectField(province, "A province", shape);
        }
    }

    /**
     * Clears what the other shape owns, so switching an address between shapes leaves
     * nothing of the old one behind. A no-op on create, where nothing was set.
     */
    private static void clearForeignFields(Address address) {
        if (address.getType() == AddressType.PROVINCE) {
            address.setLocationName(null);
            address.setStreetNo(null);
        } else if (address.getType() == AddressType.CITY) {
            address.setProvince(null);
        }
    }

    private static void requireShapeFields(Address address) {
        AddressType shape = address.getType();
        if (shape == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An address type is required: PROVINCE or CITY.");
        }
        requireField(address.getDistrict(), "A district");
        requireField(address.getCommune(), "A commune");
        requireField(address.getVillage(), "A village");
        if (shape == AddressType.PROVINCE) {
            requireField(address.getProvince(), "A province");
        } else {
            requireField(address.getLocationName(), "A location name");
            requireField(address.getStreetNo(), "A street no.");
        }
    }

    /**
     * The shape's fields as one line, smallest unit first, the way an address is spoken
     * here. Rebuilt on every write and copied onto an order at checkout, so the seller
     * reads one line whichever shape produced it.
     */
    private static String render(Address address) {
        Stream<String> parts = address.getType() == AddressType.CITY
                ? Stream.of(address.getLocationName(), address.getStreetNo(),
                        address.getVillage(), address.getCommune(), address.getDistrict())
                : Stream.of(address.getVillage(), address.getCommune(),
                        address.getDistrict(), address.getProvince());
        return parts.filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
    }

    private static void requireField(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required.");
        }
    }

    private static void rejectField(String value, String name, AddressType shape) {
        if (value != null && !value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    name + " does not belong to a " + shape + " address.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
                address.getType(),
                address.getLabel(),
                address.getRecipient(),
                address.getPhone(),
                address.getLocationName(),
                address.getStreetNo(),
                address.getProvince(),
                address.getDistrict(),
                address.getCommune(),
                address.getVillage(),
                address.getFormattedAddress(),
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
