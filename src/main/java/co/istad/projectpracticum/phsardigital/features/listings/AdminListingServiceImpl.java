package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminListingServiceImpl implements AdminListingService {

    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;

    @Override
    @Transactional
    public ListingResponse suspend(UUID uuid, SuspendRequest request) {
        Listing listing = require(uuid);
        if (listing.getStatus() == ListingStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This listing is already suspended.");
        }

        listing.setStatusBeforeSuspension(listing.getStatus());
        listing.setStatus(ListingStatus.SUSPENDED);
        listing.setModeratedBy(AuthUtils.extractUserId());
        listing.setModeratedAt(LocalDateTime.now());
        listing.setModerationReason(request.reason());

        log.info("Listing {} suspended by {}", uuid, listing.getModeratedBy());
        return listingMapper.toResponse(listingRepository.save(listing));
    }

    @Override
    @Transactional
    public ListingResponse restore(UUID uuid) {
        Listing listing = require(uuid);
        if (listing.getStatus() != ListingStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This listing is not suspended.");
        }

        // Falls back to ARCHIVED rather than ACTIVE for a row suspended before this
        // column existed: putting it back on the marketplace unasked is the worse of
        // the two guesses.
        ListingStatus restored = listing.getStatusBeforeSuspension() == null
                ? ListingStatus.ARCHIVED
                : listing.getStatusBeforeSuspension();

        listing.setStatus(restored);
        listing.setStatusBeforeSuspension(null);
        listing.setModeratedBy(AuthUtils.extractUserId());
        listing.setModeratedAt(LocalDateTime.now());
        listing.setModerationReason(null);

        log.info("Listing {} restored to {} by {}", uuid, restored, listing.getModeratedBy());
        return listingMapper.toResponse(listingRepository.save(listing));
    }

    private Listing require(UUID uuid) {
        return listingRepository.findByUuidWithDetails(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
    }
}
