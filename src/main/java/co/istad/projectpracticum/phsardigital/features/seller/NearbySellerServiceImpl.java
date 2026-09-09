package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.core.geo.GeoBoundingBox;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.review.ReviewRepository;
import co.istad.projectpracticum.phsardigital.features.seller.dto.NearbySellerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NearbySellerServiceImpl implements NearbySellerService {

    private final SellerRepository sellerRepository;
    private final ReviewRepository reviewRepository;
    private final FileUploadService fileUploadService;

    @Override
    @Transactional(readOnly = true)
    public List<NearbySellerResponse> findNearby(double latitude, double longitude,
                                                 double radiusKm, int limit) {
        GeoBoundingBox box = GeoBoundingBox.around(latitude, longitude, radiusKm);

        List<Object[]> ranked = sellerRepository.findNearestActive(
                latitude, longitude, radiusKm,
                box.minLatitude(), box.maxLatitude(),
                box.minLongitude(), box.maxLongitude(), box.wrapsAntimeridian(),
                PageRequest.of(0, limit));
        if (ranked.isEmpty()) {
            return List.of();
        }

        // Ordered, because the database already put these in the answer's order and the
        // two lookups below say nothing about distance.
        Map<String, Double> distances = new LinkedHashMap<>();
        for (Object[] row : ranked) {
            distances.put((String) row[0], ((Number) row[1]).doubleValue());
        }

        // Three queries however long the list is: the ranking, the shops, the ratings.
        Map<String, SellerProfile> profiles = new HashMap<>();
        sellerRepository.findAllById(distances.keySet())
                .forEach(profile -> profiles.put(profile.getSellerId(), profile));
        Map<String, Rating> ratings = toRatings(reviewRepository.ratingsForSellers(distances.keySet()));

        List<NearbySellerResponse> nearby = new ArrayList<>();
        distances.forEach((sellerId, distanceKm) -> {
            SellerProfile profile = profiles.get(sellerId);
            if (profile == null) {
                // The shop went away between the two queries. Skipping it beats a row the
                // client cannot open — the same call TopSellerServiceImpl makes.
                return;
            }
            Rating rating = ratings.get(sellerId);
            nearby.add(new NearbySellerResponse(
                    profile.getSellerId(),
                    profile.getBusinessName(),
                    fileUploadService.getPreviewUrl(profile.getLogoFile()),
                    profile.getAddress(),
                    profile.getCity(),
                    profile.getProvince(),
                    profile.getLatitude(),
                    profile.getLongitude(),
                    profile.getGoogleMapUrl(),
                    round(distanceKm, 2),
                    rating == null ? null : round(rating.average(), 1),
                    rating == null ? 0L : rating.count()));
        });
        return nearby;
    }

    private Map<String, Rating> toRatings(List<Object[]> rows) {
        Map<String, Rating> ratings = new HashMap<>();
        for (Object[] row : rows) {
            ratings.put((String) row[0], new Rating(((Number) row[1]).doubleValue(),
                    ((Number) row[2]).longValue()));
        }
        return ratings;
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private record Rating(double average, long count) {
    }
}
