package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingMapper;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SellerProfileServiceImpl implements SellerProfileService{

    private final SellerProfileRepository sellerProfileRepository;
    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;
    private final SellerProfileMapper sellerProfileMapper;

    @Override
    public SellerProfileResponse getPublicProfile(String sellerId) {
        SellerProfile profile = sellerProfileRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Shop not found"));
        return sellerProfileMapper.toResponse(profile);
    }

    @Override
    public Page<ListingResponse> getSellerListings(String sellerId, Pageable pageable) {
        // Verify seller exists (if not, return empty or 404 – we choose 404)
        SellerProfile seller = sellerProfileRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Shop not found"));

        Page<Listing> listings = listingRepository.findBySellerProfileAndStatus(
                seller, ListingStatus.ACTIVE, pageable);
        return listings.map(listingMapper::toResponse);
    }

    @Override
    public SellerProfileResponse getMyProfile() {
        String userId = AuthUtils.extractUserId(); // Keycloak sub
        SellerProfile profile = sellerProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Seller profile not found for this user"));
        return sellerProfileMapper.toResponse(profile);
    }

    @Override
    @Transactional
    public SellerProfileResponse updateMyProfile(SellerProfileUpdateRequest request) {
        String userId = AuthUtils.extractUserId();
        SellerProfile profile = sellerProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Seller profile not found"));

        // Partially update only fields that are not null in request
        sellerProfileMapper.updateFromRequest(request, profile);

        SellerProfile updated = sellerProfileRepository.save(profile);
        return sellerProfileMapper.toResponse(updated);
    }
}
