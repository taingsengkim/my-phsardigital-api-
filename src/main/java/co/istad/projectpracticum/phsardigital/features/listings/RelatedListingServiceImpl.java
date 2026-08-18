package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.categories.dto.CategorySummaryResponse;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.dto.RelatedListingResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelatedListingServiceImpl implements RelatedListingService {

    /** How many suggestions a caller gets when they do not say. Two rows of four. */
    private static final int DEFAULT_LIMIT = 8;

    /**
     * The ceiling on {@code limit}. A related strip is a handful of cards; past this it
     * is somebody paging the catalogue through an endpoint that fans out into three
     * queries per call.
     */
    private static final int MAX_LIMIT = 24;

    /**
     * Orders that count as a real purchase for the co-purchase signal. See
     * {@link PurchaseRepository#rankBoughtTogetherWith} for why the other two are out.
     */
    private static final Set<PurchaseStatus> ACCEPTED_ORDERS =
            EnumSet.of(PurchaseStatus.CONFIRMED, PurchaseStatus.COMPLETED);

    /**
     * The only status worth suggesting. {@code SOLD_OUT} is readable on its own page —
     * a buyer following a link deserves to be told it is gone — but putting it on a
     * strip is offering something that cannot be bought.
     */
    private static final ListingStatus BUYABLE = ListingStatus.ACTIVE;

    private final ListingRepository listingRepository;
    private final PurchaseRepository purchaseRepository;
    private final ListingVisibility listingVisibility;
    private final FileUploadService fileUploadService;

    @Override
    @Transactional(readOnly = true)
    public List<RelatedListingResponse> getRelated(UUID uuid, Integer limit) {
        Listing source = listingRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (!listingVisibility.isVisible(source)) {
            // 404, matching getListing: a listing the caller may not read should not
            // have its existence confirmed by a related-products call either.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found");
        }

        int wanted = clamp(limit);
        Map<UUID, Suggestion> picked = new LinkedHashMap<>();

        collect(picked, wanted, RelatedReason.BOUGHT_TOGETHER, () -> boughtTogetherWith(source, wanted));
        collect(picked, wanted, RelatedReason.SAME_CATEGORY, () -> categoryPeersOf(source, wanted));
        collect(picked, wanted, RelatedReason.SAME_SHOP, () -> shopPeersOf(source, wanted));

        return picked.values().stream().map(this::toResponse).toList();
    }

    /**
     * Adds one tier's candidates to the strip, keeping insertion order and skipping any
     * listing an earlier tier already claimed.
     *
     * <p>The candidates are supplied lazily rather than passed in, so a strip already
     * filled by co-purchases never runs the category and shop queries at all.
     */
    private void collect(Map<UUID, Suggestion> picked, int wanted,
                         RelatedReason reason, Supplier<List<Listing>> candidates) {
        if (picked.size() >= wanted) {
            return;
        }
        for (Listing candidate : candidates.get()) {
            if (picked.size() >= wanted) {
                return;
            }
            picked.putIfAbsent(candidate.getUuid(), new Suggestion(candidate, reason));
        }
    }

    private List<Listing> boughtTogetherWith(Listing source, int wanted) {
        List<Object[]> ranked = purchaseRepository.rankBoughtTogetherWith(
                source.getUuid(), ACCEPTED_ORDERS, topOf(wanted));
        if (ranked.isEmpty()) {
            return List.of();
        }
        List<UUID> uuids = ranked.stream().map(row -> (UUID) row[0]).toList();

        // The ranking comes from order history, which remembers listings that have since
        // been archived, sold out or taken down. Reloading drops those; the map then
        // puts the survivors back into co-purchase order, which the reload lost.
        Map<UUID, Listing> buyable = listingRepository.findBuyableByUuidIn(uuids, BUYABLE).stream()
                .collect(Collectors.toMap(Listing::getUuid, Function.identity()));
        return uuids.stream().map(buyable::get).filter(Objects::nonNull).toList();
    }

    private List<Listing> categoryPeersOf(Listing source, int wanted) {
        return listingRepository.findCategoryPeers(
                source.getCategory(), source.getUuid(), source.getPrice(), BUYABLE, topOf(wanted));
    }

    private List<Listing> shopPeersOf(Listing source, int wanted) {
        return listingRepository.findShopPeers(
                source.getSellerProfile(), source.getUuid(), BUYABLE, topOf(wanted));
    }

    /**
     * Asks each tier for a full strip's worth rather than only the shortfall.
     *
     * <p>A later tier's rows may already be on the strip, and those duplicates are only
     * discovered after the query has run. At most {@code wanted} listings can have been
     * picked by then, so fetching {@code wanted} always leaves enough new ones to fill
     * the remainder — asking for just the shortfall would return a short strip whenever
     * the tiers overlap, which for two same-shop tiers is most of the time.
     */
    private Pageable topOf(int wanted) {
        return PageRequest.of(0, wanted);
    }

    private int clamp(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private RelatedListingResponse toResponse(Suggestion suggestion) {
        Listing listing = suggestion.listing();
        SellerProfile shop = listing.getSellerProfile();
        return new RelatedListingResponse(
                listing.getUuid(),
                listing.getTitle(),
                listing.getSlug(),
                listing.getPrice(),
                listing.getStockQty(),
                listing.getSold(),
                fileUploadService.getPreviewUrl(listing.getThumbnailFile()),
                new CategorySummaryResponse(listing.getCategory().getName(), listing.getCategory().getSlug()),
                shop.getSellerId(),
                shop.getBusinessName(),
                suggestion.reason());
    }

    /** A candidate and the tier that put it on the strip. */
    private record Suggestion(Listing listing, RelatedReason reason) {
    }
}
