package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.core.geo.GeoBoundingBox;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.review.ReviewRepository;
import co.istad.projectpracticum.phsardigital.features.seller.dto.NearbySellerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NearbySellerServiceImplTest {

    private static final double LATITUDE = 11.5564;
    private static final double LONGITUDE = 104.9282;

    @Mock private SellerRepository sellerRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private FileUploadService fileUploadService;

    private NearbySellerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NearbySellerServiceImpl(sellerRepository, reviewRepository, fileUploadService);
        when(reviewRepository.ratingsForSellers(anyCollection())).thenReturn(List.of());
    }

    @Test
    @DisplayName("keeps the order the database ranked the shops in")
    void keepsDatabaseOrder() {
        when(sellerRepository.findNearestActive(anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyBoolean(), any(Pageable.class)))
                .thenReturn(List.of(
                        row("near", 0.4),
                        row("middling", 2.5),
                        row("far", 8.1)));
        when(sellerRepository.findAllById(anyIterable())).thenReturn(List.of(
                shop("far"), shop("near"), shop("middling")));

        List<NearbySellerResponse> nearby = service.findNearby(LATITUDE, LONGITUDE, 10, 10);

        assertThat(nearby).extracting(NearbySellerResponse::sellerId)
                .containsExactly("near", "middling", "far");
        assertThat(nearby).extracting(NearbySellerResponse::distanceKm)
                .containsExactly(0.4, 2.5, 8.1);
    }

    @Test
    @DisplayName("asks the database for the box around the point it was given")
    void passesTheBoundingBox() {
        when(sellerRepository.findNearestActive(anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyBoolean(), any(Pageable.class)))
                .thenReturn(List.of());

        service.findNearby(LATITUDE, LONGITUDE, 25, 7);

        GeoBoundingBox expected = GeoBoundingBox.around(LATITUDE, LONGITUDE, 25);
        ArgumentCaptor<Double> minLatitude = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> maxLatitude = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sellerRepository).findNearestActive(anyDouble(), anyDouble(), anyDouble(),
                minLatitude.capture(), maxLatitude.capture(), anyDouble(), anyDouble(),
                anyBoolean(), pageable.capture());

        assertThat(minLatitude.getValue()).isEqualTo(expected.minLatitude());
        assertThat(maxLatitude.getValue()).isEqualTo(expected.maxLatitude());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(7);
        assertThat(pageable.getValue().getSort().isSorted())
                .as("a native query cannot take a Sort - the ORDER BY is in the SQL")
                .isFalse();
    }

    @Test
    @DisplayName("does not go looking for shops when nothing is in range")
    void nothingInRangeCostsOneQuery() {
        when(sellerRepository.findNearestActive(anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyBoolean(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.findNearby(LATITUDE, LONGITUDE, 5, 10)).isEmpty();

        verify(sellerRepository, never()).findAllById(anyIterable());
        verify(reviewRepository, never()).ratingsForSellers(anyCollection());
    }

    @Test
    @DisplayName("attaches the shop's rating, and reports no reviews as unrated rather than zero stars")
    void attachesRating() {
        when(sellerRepository.findNearestActive(anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyBoolean(), any(Pageable.class)))
                .thenReturn(List.of(row("rated", 1.0), row("unrated", 2.0)));
        when(sellerRepository.findAllById(anyIterable()))
                .thenReturn(List.of(shop("rated"), shop("unrated")));
        when(reviewRepository.ratingsForSellers(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{"rated", 4.26, 17L}));

        List<NearbySellerResponse> nearby = service.findNearby(LATITUDE, LONGITUDE, 10, 10);

        assertThat(nearby.getFirst().averageRating()).isEqualTo(4.3);
        assertThat(nearby.getFirst().reviewCount()).isEqualTo(17);
        assertThat(nearby.getLast().averageRating()).isNull();
        assertThat(nearby.getLast().reviewCount()).isZero();
    }

    @Test
    @DisplayName("rounds the distance to ten metres")
    void roundsDistance() {
        when(sellerRepository.findNearestActive(anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyBoolean(), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(row("shop", 1.23456789)));
        when(sellerRepository.findAllById(anyIterable())).thenReturn(List.of(shop("shop")));

        assertThat(service.findNearby(LATITUDE, LONGITUDE, 10, 10).getFirst().distanceKm())
                .isEqualTo(1.23);
    }

    @Test
    @DisplayName("drops a shop that disappeared between the ranking and the lookup")
    void skipsVanishedShop() {
        when(sellerRepository.findNearestActive(anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyBoolean(), any(Pageable.class)))
                .thenReturn(List.of(row("gone", 0.5), row("still-here", 3.0)));
        when(sellerRepository.findAllById(anyIterable())).thenReturn(List.of(shop("still-here")));

        assertThat(service.findNearby(LATITUDE, LONGITUDE, 10, 10))
                .extracting(NearbySellerResponse::sellerId)
                .containsExactly("still-here");
    }

    @Test
    @DisplayName("answers the pin and the map link so a client can draw the results")
    void carriesTheLocation() {
        SellerProfile shop = shop("shop");
        shop.setLatitude(new BigDecimal("11.55640000"));
        shop.setLongitude(new BigDecimal("104.92820000"));
        shop.setGoogleMapUrl("https://maps.app.goo.gl/abc");
        shop.setAddress("12 Street 240");
        shop.setCity("Phnom Penh");
        shop.setProvince("Phnom Penh");

        when(sellerRepository.findNearestActive(anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyBoolean(), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(row("shop", 0.1)));
        when(sellerRepository.findAllById(anyIterable())).thenReturn(List.of(shop));

        NearbySellerResponse response = service.findNearby(LATITUDE, LONGITUDE, 10, 10).getFirst();

        assertThat(response.latitude()).isEqualByComparingTo("11.5564");
        assertThat(response.longitude()).isEqualByComparingTo("104.9282");
        assertThat(response.googleMapUrl()).isEqualTo("https://maps.app.goo.gl/abc");
        assertThat(response.address()).isEqualTo("12 Street 240");
        assertThat(response.city()).isEqualTo("Phnom Penh");
        assertThat(response.province()).isEqualTo("Phnom Penh");
    }

    /** A ranking row as the native query hands it over: id, then distance. */
    private static Object[] row(String sellerId, double distanceKm) {
        return new Object[]{sellerId, distanceKm};
    }

    private static SellerProfile shop(String sellerId) {
        SellerProfile profile = new SellerProfile(sellerId);
        profile.setBusinessName(sellerId + " shop");
        profile.setIsActive(true);
        return profile;
    }
}

