package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.review.ReviewRepository;
import co.istad.projectpracticum.phsardigital.features.seller.dto.TopSellerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TopSellerServiceImpl implements TopSellerService {

    /** Only settled orders count — the rest are money that has not moved, or never will. */
    private static final PurchaseStatus SETTLED = PurchaseStatus.COMPLETED;

    /**
     * How many reviews a shop needs before its average can put it on the board. Low
     * enough that a young marketplace still produces a list, high enough that one
     * enthusiastic customer cannot crown a shop.
     */
    private static final long MIN_REVIEWS_FOR_RATING = 3;

    private final PurchaseRepository purchaseRepository;
    private final ReviewRepository reviewRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final FileUploadService fileUploadService;

    @Override
    @Transactional(readOnly = true)
    public List<TopSellerResponse> getTopSellers(TopSellerBasis basis, TopSellerPeriod period, int limit) {
        LocalDateTime since = period.since(LocalDateTime.now());
        PageRequest topN = PageRequest.of(0, limit);

        List<Object[]> ranked = switch (basis) {
            case REVENUE -> purchaseRepository.rankSellersByRevenue(SETTLED, since, topN);
            case ORDERS -> purchaseRepository.rankSellersByOrders(SETTLED, since, topN);
            case RATING -> reviewRepository.rankSellersByRating(since, MIN_REVIEWS_FOR_RATING, topN);
        };
        if (ranked.isEmpty()) {
            return List.of();
        }

        List<String> sellerIds = ranked.stream().map(row -> (String) row[0]).toList();

        // Three queries for the whole board however long it is: the ranking, the metric
        // it was not ranked on, and the shops themselves.
        Map<String, Rating> ratings = basis == TopSellerBasis.RATING
                ? toRatings(ranked)
                : toRatings(reviewRepository.ratingsForSellers(since, sellerIds));
        Map<String, Sales> sales = basis == TopSellerBasis.RATING
                ? toSales(purchaseRepository.salesForSellers(SETTLED, since, sellerIds))
                : toSales(ranked);

        Map<String, SellerProfile> profiles = new HashMap<>();
        sellerProfileRepository.findAllById(sellerIds)
                .forEach(profile -> profiles.put(profile.getSellerId(), profile));

        List<Entry> entries = new ArrayList<>();
        for (String sellerId : sellerIds) {
            SellerProfile profile = profiles.get(sellerId);
            if (profile == null) {
                // The shop went away between the aggregate and this lookup. Skipping it
                // is better than a row the client cannot open.
                continue;
            }
            Rating rating = ratings.get(sellerId);
            Sales shopSales = sales.get(sellerId);
            entries.add(new Entry(profile, primaryOf(basis, rating, shopSales), rating, shopSales));
        }

        // The database ordered by the primary metric; rating breaks ties, so two shops
        // level on orders are not separated by whichever row the scan reached first.
        entries.sort(Comparator
                .comparingDouble(Entry::primary).reversed()
                .thenComparing(entry -> entry.rating() == null ? 0d : entry.rating().average(),
                        Comparator.reverseOrder()));

        List<TopSellerResponse> board = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            board.add(toResponse(index + 1, entries.get(index)));
        }
        return board;
    }

    private double primaryOf(TopSellerBasis basis, Rating rating, Sales sales) {
        return switch (basis) {
            case REVENUE -> sales == null ? 0d : sales.revenue();
            case ORDERS -> sales == null ? 0d : sales.orders();
            case RATING -> rating == null ? 0d : rating.average();
        };
    }

    private TopSellerResponse toResponse(int rank, Entry entry) {
        SellerProfile profile = entry.profile();
        return new TopSellerResponse(
                rank,
                profile.getSellerId(),
                profile.getBusinessName(),
                fileUploadService.getPreviewUrl(profile.getLogoFile()),
                profile.getCity(),
                profile.getProvince(),
                entry.rating() == null ? null : round(entry.rating().average()),
                entry.rating() == null ? 0L : entry.rating().count(),
                entry.sales() == null ? 0L : entry.sales().orders());
    }

    private Map<String, Rating> toRatings(List<Object[]> rows) {
        Map<String, Rating> ratings = new HashMap<>();
        for (Object[] row : rows) {
            ratings.put((String) row[0], new Rating(((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()));
        }
        return ratings;
    }

    private Map<String, Sales> toSales(List<Object[]> rows) {
        Map<String, Sales> sales = new HashMap<>();
        for (Object[] row : rows) {
            sales.put((String) row[0], new Sales(((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()));
        }
        return sales;
    }

    private static Double round(double average) {
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private record Rating(double average, long count) {
    }

    private record Sales(double revenue, long orders) {
    }

    private record Entry(SellerProfile profile, double primary, Rating rating, Sales sales) {
    }
}
