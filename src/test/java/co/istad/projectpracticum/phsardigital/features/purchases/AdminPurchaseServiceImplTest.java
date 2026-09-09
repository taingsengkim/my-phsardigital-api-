package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseRowResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseActivityResponse;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminPurchaseServiceImplTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 10, 1, 0, 0);

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private PurchaseMapper purchaseMapper;
    @Mock private FileUploadService fileUploadService;

    private AdminPurchaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminPurchaseServiceImpl(
                purchaseRepository, userProfileRepository, purchaseMapper, fileUploadService);
        when(purchaseRepository.findItemsForPurchases(anyCollection())).thenReturn(List.of());
        when(userProfileRepository.findAllById(anyIterable())).thenReturn(List.of());
        when(purchaseRepository.orderStatsForBuyers(anyString(), anyCollection()))
                .thenReturn(List.of());
    }

    // ---------------------------------------------------------------- summary

    @Test
    @DisplayName("merchandise value leaves out cancelled orders")
    void gmvExcludesCancelled() {
        summariseWindows(
                rows(statusRow("COMPLETED", 10, "1000.00"),
                        statusRow("CANCELLED", 4, "400.00")),
                List.of());

        AdminPurchaseSummaryResponse summary = service.summarise(FROM, TO);

        assertThat(summary.grossMerchandiseValue()).isEqualByComparingTo("1000.00");
        assertThat(summary.totalOrders())
                .as("cancelled orders still happened, so they are still counted")
                .isEqualTo(14);
    }

    @Test
    @DisplayName("every status has a badge, including the ones with no orders")
    void everyStatusIsPresent() {
        summariseWindows(rows(statusRow("PENDING", 3, "60.00")), List.of());

        AdminPurchaseSummaryResponse summary = service.summarise(FROM, TO);

        assertThat(summary.byStatus()).containsOnlyKeys(PurchaseStatus.values());
        assertThat(summary.byStatus().get(PurchaseStatus.COMPLETED).orderCount()).isZero();
        assertThat(summary.byStatus().get(PurchaseStatus.COMPLETED).value())
                .isEqualByComparingTo("0.00");
        assertThat(summary.needingAction()).isEqualTo(3);
    }

    @Test
    @DisplayName("average order value is over orders that were not cancelled")
    void averageIgnoresCancelled() {
        summariseWindows(
                rows(statusRow("COMPLETED", 4, "100.00"),
                        statusRow("CANCELLED", 6, "600.00")),
                List.of());

        assertThat(service.summarise(FROM, TO).averageOrderValue())
                .as("100.00 over 4 selling orders, not over all 10")
                .isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("growth compares against the preceding window of equal length")
    void growthComparesPrecedingWindow() {
        summariseWindows(
                rows(statusRow("COMPLETED", 2, "114.00")),
                rows(statusRow("COMPLETED", 2, "100.00")));

        AdminPurchaseSummaryResponse summary = service.summarise(FROM, TO);

        assertThat(summary.growthPercent()).isEqualTo(14.0);
        assertThat(summary.previousGrossMerchandiseValue()).isEqualByComparingTo("100.00");
        // The earlier window is the 31 days immediately before this one.
        verify(purchaseRepository).summariseOrdersPlacedBetween(FROM.minusDays(30), FROM);
    }

    @Test
    @DisplayName("growth is null rather than zero when there is nothing to compare against")
    void growthIsNullFromZero() {
        summariseWindows(rows(statusRow("COMPLETED", 2, "500.00")), List.of());

        assertThat(service.summarise(FROM, TO).growthPercent()).isNull();
    }

    @Test
    @DisplayName("an empty window reports zeroes, not nulls")
    void emptyWindowIsZero() {
        summariseWindows(List.of(), List.of());

        AdminPurchaseSummaryResponse summary = service.summarise(FROM, TO);

        assertThat(summary.grossMerchandiseValue()).isEqualByComparingTo("0.00");
        assertThat(summary.averageOrderValue()).isEqualByComparingTo("0.00");
        assertThat(summary.totalOrders()).isZero();
    }

    // ------------------------------------------------------------- activities

    @Test
    @DisplayName("builds the timeline from the moments the order actually recorded")
    void timelineFollowsTheTimestamps() {
        Purchase order = onlineOrder();
        order.setConfirmedAt(FROM.plusHours(1));
        order.setCompletedAt(FROM.plusDays(1));
        when(purchaseRepository.findById(order.getUuid())).thenReturn(java.util.Optional.of(order));

        List<PurchaseActivityResponse> timeline = service.activities(order.getUuid());

        assertThat(timeline).extracting(PurchaseActivityResponse::action).containsExactly(
                PurchaseActivityAction.ORDER_PLACED,
                PurchaseActivityAction.ORDER_CONFIRMED,
                PurchaseActivityAction.ORDER_COMPLETED);
        assertThat(timeline).extracting(PurchaseActivityResponse::timestamp).isSorted();
    }

    @Test
    @DisplayName("a state the order never reached produces no entry")
    void unreachedStatesAreAbsent() {
        Purchase order = onlineOrder();
        when(purchaseRepository.findById(order.getUuid())).thenReturn(java.util.Optional.of(order));

        assertThat(service.activities(order.getUuid()))
                .extracting(PurchaseActivityResponse::action)
                .containsExactly(PurchaseActivityAction.ORDER_PLACED);
    }

    @Test
    @DisplayName("a cancellation names nobody, because nothing records who cancelled")
    void cancellationHasNoActor() {
        Purchase order = onlineOrder();
        order.setCancelledAt(FROM.plusHours(2));
        when(purchaseRepository.findById(order.getUuid())).thenReturn(java.util.Optional.of(order));

        PurchaseActivityResponse cancelled = service.activities(order.getUuid()).getLast();

        assertThat(cancelled.actorType()).isEqualTo(PurchaseActorType.UNKNOWN);
        assertThat(cancelled.actorName()).isNull();
    }

    @Test
    @DisplayName("a counter sale is attributed to the shop, not to a buyer who does not exist")
    void counterSaleIsAttributedToTheShop() {
        Purchase order = onlineOrder();
        order.setBuyerId(null);
        order.setChannel(PurchaseChannel.POS);
        when(purchaseRepository.findById(order.getUuid())).thenReturn(java.util.Optional.of(order));

        PurchaseActivityResponse placed = service.activities(order.getUuid()).getFirst();

        assertThat(placed.actorType()).isEqualTo(PurchaseActorType.SELLER);
        assertThat(placed.actorName()).isEqualTo("Phsar Tech");
    }

    // -------------------------------------------------------------- the table

    @Test
    @DisplayName("looks buyers up once for the page, not once per order")
    void buyersAreFetchedInOneQuery() {
        Purchase first = onlineOrder();
        Purchase second = onlineOrder();
        second.setBuyerId(first.getBuyerId());          // the same person ordering twice
        Purchase third = onlineOrder();
        third.setBuyerId("usr_other");
        pageOf(List.of(first, second, third));

        service.list(emptyFilter(), 0, 20, null, null);

        verify(userProfileRepository, times(1)).findAllById(anyIterable());
        verify(purchaseRepository, times(1)).findItemsForPurchases(anyCollection());
    }

    @Test
    @DisplayName("a counter sale has no buyer block at all")
    void counterSaleHasNoBuyer() {
        Purchase counterSale = onlineOrder();
        counterSale.setBuyerId(null);
        pageOf(List.of(counterSale));

        assertThat(service.list(emptyFilter(), 0, 20, null, null)
                .getContent().getFirst().buyer()).isNull();
    }

    @Test
    @DisplayName("counts distinct products and total units separately")
    void itemSummaryCountsLinesAndUnits() {
        Purchase order = onlineOrder();
        pageOf(List.of(order));
        when(purchaseRepository.findItemsForPurchases(anyCollection()))
                .thenReturn(List.of(item(order, 2), item(order, 10)));

        AdminPurchaseRowResponse row = service.list(emptyFilter(), 0, 20, null, null)
                .getContent().getFirst();

        assertThat(row.items().lineCount()).isEqualTo(2);
        assertThat(row.items().unitCount())
                .as("one product ordered twelve times is not a one-item order")
                .isEqualTo(12);
        assertThat(row.items().hasMoreItems()).isTrue();
    }

    @Test
    @DisplayName("keeps the buyer's name from the order over the account holder's")
    void recipientNameWins() {
        Purchase order = onlineOrder();
        order.setRecipientName("Dara's mother");
        UserProfile account = new UserProfile();
        ReflectionTestUtils.setField(account, "id", order.getBuyerId());
        ReflectionTestUtils.setField(account, "fullName", "Dara Chan");
        pageOf(List.of(order));
        when(userProfileRepository.findAllById(anyIterable())).thenReturn(List.of(account));

        assertThat(service.list(emptyFilter(), 0, 20, null, null)
                .getContent().getFirst().buyer().name()).isEqualTo("Dara's mother");
    }

    // ---------------------------------------------------------------- export

    @Test
    @DisplayName("an export with no matching orders is a header and nothing else")
    void emptyExportIsJustTheHeader() {
        when(purchaseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(500), 0));

        assertThat(service.exportCsv(emptyFilter(), null, null))
                .isEqualTo(AdminPurchaseCsvWriter.HEADER);
    }

    @Test
    @DisplayName("exports every matching order, not just the first page of them")
    void exportWalksPastTheFirstPage() {
        Purchase order = onlineOrder();
        Page<Purchase> firstPage = new PageImpl<>(List.of(order), Pageable.ofSize(500), 501);
        Page<Purchase> lastPage = new PageImpl<>(List.of(onlineOrder()),
                org.springframework.data.domain.PageRequest.of(1, 500), 501);
        when(purchaseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(firstPage, lastPage);

        String csv = service.exportCsv(emptyFilter(), null, null);

        assertThat(csv.strip().split("\r\n"))
                .as("header plus one row from each page")
                .hasSize(3);
    }

    // ---------------------------------------------------------------- helpers

    private void summariseWindows(List<Object[]> current, List<Object[]> previous) {
        when(purchaseRepository.summariseOrdersPlacedBetween(eq(FROM), eq(TO))).thenReturn(current);
        when(purchaseRepository.summariseOrdersPlacedBetween(eq(FROM.minusDays(30)), eq(FROM)))
                .thenReturn(previous);
    }

    private void pageOf(List<Purchase> purchases) {
        when(purchaseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(purchases, Pageable.ofSize(20), purchases.size()));
    }

    private static Object[] statusRow(String status, long count, String value) {
        return new Object[]{status, count, new BigDecimal(value)};
    }

    /** {@code List.of} spreads a lone {@code Object[]} into varargs; this does not. */
    private static List<Object[]> rows(Object[]... aggregateRows) {
        return List.of(aggregateRows);
    }

    private static AdminPurchaseFilter emptyFilter() {
        return new AdminPurchaseFilter(null, null, null, null, null, null, null, null);
    }

    private static Purchase onlineOrder() {
        SellerProfile shop = new SellerProfile("str_1");
        shop.setBusinessName("Phsar Tech");
        shop.setIsActive(true);

        Purchase purchase = new Purchase();
        purchase.setUuid(UUID.randomUUID());
        purchase.setBuyerId("usr_" + UUID.randomUUID());
        purchase.setSellerProfile(shop);
        purchase.setTotalPrice(new BigDecimal("145.50"));
        purchase.setStatus(PurchaseStatus.PENDING);
        purchase.setChannel(PurchaseChannel.ONLINE);
        ReflectionTestUtils.setField(purchase, "createdAt", FROM);
        return purchase;
    }

    private static PurchaseItem item(Purchase purchase, int quantity) {
        var listing = new co.istad.projectpracticum.phsardigital.features.listings.Listing();
        ReflectionTestUtils.setField(listing, "title", "Keyboard");

        PurchaseItem item = new PurchaseItem();
        item.setPurchase(purchase);
        item.setListing(listing);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("70.00"));
        return item;
    }
}
