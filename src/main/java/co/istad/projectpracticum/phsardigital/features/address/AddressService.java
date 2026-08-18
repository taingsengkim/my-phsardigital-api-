package co.istad.projectpracticum.phsardigital.features.address;

import co.istad.projectpracticum.phsardigital.features.address.dto.AddressRequest;
import co.istad.projectpracticum.phsardigital.features.address.dto.AddressResponse;
import co.istad.projectpracticum.phsardigital.features.address.dto.UpdateAddressRequest;

import java.util.List;
import java.util.UUID;

public interface AddressService {

    /** The caller's saved addresses, default first. */
    List<AddressResponse> getMyAddresses();

    AddressResponse create(AddressRequest request);

    AddressResponse update(UUID id, UpdateAddressRequest request);

    /** Promotes one address to default, demoting whichever held it before. */
    AddressResponse makeDefault(UUID id);

    void delete(UUID id);

    /**
     * Resolves an address the caller owns, for checkout to copy from.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 when it does
     *         not exist or belongs to somebody else
     */
    Address requireOwned(UUID id, String userId);
}
