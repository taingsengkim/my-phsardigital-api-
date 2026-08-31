package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerDashboardServiceImplTest {

    private static final String SELLER = "seller-1";

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private FileUploadService fileUploadService;

    private SellerDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SellerDashboardServiceImpl(
                purchaseRepository, listingRepository, fileUploadService);
        ReflectionTestUtils.setField(service, "lowStockThreshold", 5);

        // Sensible empties so each test only stubs what it is about.
        when(purchaseRepository.summariseOrdersForSeller(SELLER)).thenReturn(List.of());
        when(purchaseRepository.dailySalesForSeller(any(), any())).thenReturn(List.of());
        when(purchaseRepository.rankSellerProductsByUnits(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(purchaseRepository.sumSalesPlacedBetween(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(listingRepository.summariseInventoryForSeller(SELLER)).thenReturn(List.of());
    }

    @Test
    void deliveredMoneyAndPromisedMoneyAreReportedSeparately() {
        // Adding them would tell a shop it has taken money it has not collected.
        when(purchaseRepository.summariseOrdersForSeller(SELLER)).thenReturn(List.of(
                new Object[]{"PENDING", 4L, new BigDecimal("200.00")},
                new Object[]{"CONFIRMED", 6L, new BigDecimal("150.00")},
                new Object[]{"COMPLETED", 290L, new BigDecimal("12450.00")},
                new Object[]{"CANCELLED", 12L, new BigDecimal("99.00")}
        ));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var overview = service.getMyOverview();

            assertThat(overview.revenue().lifetimeEarned()).isEqualByComparingTo("12450.00");
            assertThat(overview.revenue().inFlight()).isEqualByComparingTo("350.00");
            assertThat(overview.orders().total()).isEqualTo(312);
            assertThat(overview.orders().pending()).isEqualTo(4);
            assertThat(overview.orders().completed()).isEqualTo(290);
        }
    }

    @Test
    void growthFromNothingIsNullRatherThanAFabricatedPercentage() {
        // A shop's first week has no previous week to grow from. Reporting 100% there
        // would be inventing a number.
        when(purchaseRepository.sumSalesPlacedBetween(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThat(service.getMyOverview().revenue().percentageGrowth()).isNull();
        }
    }

    @Test
    void growthComparesTheLastSevenDaysWithTheSevenBefore() {
        // Two adjacent windows, so an order falls in exactly one of them.
        when(purchaseRepository.sumSalesPlacedBetween(any(), any(), any()))
                .thenAnswer(call -> {
                    LocalDateTime from = call.getArgument(1);
                    LocalDate midnight = LocalDate.now();
                    if (from.toLocalDate().isEqual(midnight.minusDays(6))) {
                        return new BigDecimal("1200.00");   // this week
                    }
                    if (from.toLocalDate().isEqual(midnight.minusDays(13))) {
                        return new BigDecimal("1000.00");   // the week before
                    }
                    return BigDecimal.ZERO;
                });

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            assertThat(service.getMyOverview().revenue().percentageGrowth())
                    .isEqualByComparingTo("20.0");
        }
    }

    @Test
    void theChartAlwaysHasSevenDaysEvenWhereNothingSold() {
        // The query returns only days with orders; a chart with holes in its axis is
        // worse than one with zeroes.
        LocalDate today = LocalDate.now();
        when(purchaseRepository.dailySalesForSeller(eq(SELLER), any())).thenReturn(List.of(
                new Object[]{java.sql.Date.valueOf(today), 9L, new BigDecimal("350.00")},
                new Object[]{java.sql.Date.valueOf(today.minusDays(3)), 8L, new BigDecimal("310.00")}
        ));

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var chart = service.getMyOverview().salesChart7Days();

            assertThat(chart).hasSize(7);
            assertThat(chart.getFirst().date()).isEqualTo(today.minusDays(6));
            assertThat(chart.getLast().date()).isEqualTo(today);
            assertThat(chart.getLast().ordersCount()).isEqualTo(9);
            assertThat(chart.getLast().revenue()).isEqualByComparingTo("350.00");
            // A day with no orders is a zero, not a gap.
            assertThat(chart.get(1).ordersCount()).isZero();
            assertThat(chart.get(1).revenue()).isEqualByComparingTo("0.00");
            assertThat(chart).allSatisfy(day -> assertThat(day.dayLabel()).isNotBlank());
        }
    }

    @Test
    void inventoryCountsEachStateAndOnlyStocksWhatIsOnSale() {
        when(listingRepository.summariseInventoryForSeller(SELLER)).thenReturn(List.of(
                new Object[]{ListingStatus.ACTIVE, 72L, 1250L},
                new Object[]{ListingStatus.DRAFT, 4L, 40L},
                new Object[]{ListingStatus.SOLD_OUT, 8L, 0L}
        ));
        when(listingRepository.countLowStockForSeller(eq(SELLER), eq(ListingStatus.ACTIVE), anyInt()))
                .thenReturn(3L);

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var inventory = service.getMyOverview().inventory();

            assertThat(inventory.totalProducts()).isEqualTo(84);
            assertThat(inventory.activeProducts()).isEqualTo(72);
            assertThat(inventory.draftProducts()).isEqualTo(4);
            assertThat(inventory.soldOutProducts()).isEqualTo(8);
            assertThat(inventory.lowStockProducts()).isEqualTo(3);
            // Draft stock is excluded: nobody can order it.
            assertThat(inventory.totalInventoryUnits()).isEqualTo(1250);
        }
    }

    @Test
    void bestSellersAreRankedFromAcceptedOrdersOnly() {
        UUID listingUuid = UUID.randomUUID();
        when(purchaseRepository.rankSellerProductsByUnits(eq(SELLER), any(), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{listingUuid, 54L, new BigDecimal("8100.00")}));
        when(listingRepository.findAllById(List.of(listingUuid)))
                .thenReturn(List.of(listing(listingUuid, "Dior Savage Elixir")));
        when(fileUploadService.getPreviewUrl(any(FileUpload.class)))
                .thenReturn("https://cdn/dior.jpg");

        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var top = service.getMyOverview().topProducts();

            assertThat(top).singleElement().satisfies(product -> {
                assertThat(product.listingUuid()).isEqualTo(listingUuid);
                assertThat(product.title()).isEqualTo("Dior Savage Elixir");
                assertThat(product.slug()).isEqualTo("dior-savage-elixir");
                assertThat(product.thumbnailUrl()).isEqualTo("https://cdn/dior.jpg");
                assertThat(product.unitsSold()).isEqualTo(54);
                assertThat(product.totalRevenue()).isEqualByComparingTo("8100.00");
            });
        }

        // Pending orders nobody accepted, and cancelled ones, must not shape the ranking.
        org.mockito.ArgumentCaptor<java.util.Collection<PurchaseStatus>> statuses =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        org.mockito.Mockito.verify(purchaseRepository)
                .rankSellerProductsByUnits(eq(SELLER), statuses.capture(), any(Pageable.class));
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(PurchaseStatus.CONFIRMED, PurchaseStatus.COMPLETED);
    }

    @Test
    void aBrandNewShopAnswersWithZeroesRatherThanFailing() {
        try (MockedStatic<AuthUtils> auth = authenticatedSeller()) {
            var overview = service.getMyOverview();

            assertThat(overview.orders().total()).isZero();
            assertThat(overview.revenue().lifetimeEarned()).isEqualByComparingTo("0.00");
            assertThat(overview.revenue().inFlight()).isEqualByComparingTo("0.00");
            assertThat(overview.inventory().totalProducts()).isZero();
            assertThat(overview.salesChart7Days()).hasSize(7);
            assertThat(overview.topProducts()).isEmpty();
        }
    }

    private static MockedStatic<AuthUtils> authenticatedSeller() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(SELLER);
        return auth;
    }

    private static Listing listing(UUID uuid, String title) {
        FileUpload thumbnail = new FileUpload();
        thumbnail.setId(UUID.randomUUID());

        Listing listing = new Listing();
        listing.setUuid(uuid);
        listing.setTitle(title);
        listing.setSlug(title.toLowerCase().replace(' ', '-'));
        listing.setThumbnailFile(thumbnail);
        return listing;
    }
}
