package co.istad.projectpracticum.phsardigital.features.admin;

import co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryAvailability;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.seller.SellerRepository;
import co.istad.projectpracticum.phsardigital.features.seller.application.ApplicationStatus;
import co.istad.projectpracticum.phsardigital.features.seller.application.SellerApplicationDocumentRepository;
import co.istad.projectpracticum.phsardigital.features.seller.application.SellerApplicationRepository;
import co.istad.projectpracticum.phsardigital.features.subscription.SellerSubscriptionRepository;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlan;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlanRepository;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionStatus;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.ApplicationSummary;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.ListingSummary;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.Money;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.PlanSubscriptionCount;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.PurchaseSummary;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.SellerSummary;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.SubscriptionSummary;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.UserSummary;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    /** The only order state whose money has actually changed hands. */
    private static final PurchaseStatus SETTLED = PurchaseStatus.COMPLETED;
    private static final String MARKETPLACE_CURRENCY_CODE = "USD";

    private final UserProfileRepository userRepository;
    private final SellerRepository sellerRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryAvailability categoryAvailability;
    private final ListingRepository listingRepository;
    private final SellerApplicationRepository applicationRepository;
    private final SellerApplicationDocumentRepository applicationDocumentRepository;
    private final PurchaseRepository purchaseRepository;
    private final SellerSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final Clock clock;

    /**
     * PostgreSQL's repeatable-read snapshot keeps independently aggregated cards from
     * observing different commits during the same response.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminDashboardSummaryResponse getSummary() {
        // The first query establishes the database snapshot. Capture the public
        // timestamp immediately afterwards from an injectable clock.
        long totalUsers = userRepository.count();
        Instant asOf = clock.instant();
        LocalDateTime subscriptionEvaluationTime = LocalDateTime.ofInstant(asOf, clock.getZone());
        List<PlanSubscriptionCount> byPlan = activeSubscriptionsByPlan(
                subscriptionEvaluationTime);

        return new AdminDashboardSummaryResponse(
                new UserSummary(
                        totalUsers,
                        purchaseRepository.countDistinctBuyersByStatus(SETTLED)),
                new SellerSummary(
                        sellerRepository.count(),
                        sellerRepository.countByIsActiveTrue()),
                new ListingSummary(
                        listingRepository.count(),
                        countPubliclyAvailableListings()),
                new ApplicationSummary(
                        applicationRepository.countByStatus(ApplicationStatus.PENDING),
                        applicationDocumentRepository.countByApplicationStatus(
                                ApplicationStatus.PENDING)),
                new PurchaseSummary(
                        purchaseRepository.countByStatus(SETTLED),
                        new Money(completedGmv(), MARKETPLACE_CURRENCY_CODE)),
                new SubscriptionSummary(byPlan),
                asOf);
    }

    /**
     * A listing's direct category flags are insufficient: an unavailable ancestor
     * hides the whole branch. Resolve the effective category set with the same
     * cycle- and depth-aware rule used by public catalogue reads, then count only
     * listings assigned to that set.
     */
    private long countPubliclyAvailableListings() {
        Set<UUID> categoryUuids = categoryRepository
                .findAllByIsDeletedFalseAndIsActiveTrue(Sort.unsorted())
                .stream()
                .filter(categoryAvailability::isEffectivelyActive)
                .map(Category::getUuid)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (categoryUuids.isEmpty()) {
            return 0L;
        }
        return listingRepository.countBuyableByStatusInCategories(
                ListingStatus.ACTIVE, categoryUuids);
    }

    /**
     * Seeded from the whole catalogue, so a plan dropping to zero subscribers stays in
     * the response instead of disappearing from the chart. Retired plans are included
     * too: somebody may still be inside a period on one.
     *
     * <p>A counted code with no catalogue row is appended rather than dropped. The
     * response contract requires the breakdown to sum to the active total, so silently
     * discarding an unknown code would fail the whole dashboard instead of showing the
     * one row that needs attention.
     */
    private List<PlanSubscriptionCount> activeSubscriptionsByPlan(LocalDateTime now) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : subscriptionRepository.countActiveByPlan(
                SubscriptionStatus.ACTIVE, now)) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }

        List<PlanSubscriptionCount> breakdown = new ArrayList<>();
        for (SubscriptionPlan plan : planRepository.findAllByOrderBySortOrderAsc()) {
            Long counted = counts.remove(plan.getCode());
            breakdown.add(new PlanSubscriptionCount(
                    plan.getCode(),
                    plan.getDisplayName(),
                    counted == null ? 0L : counted));
        }

        // Whatever the catalogue could not explain, labelled by its bare code.
        counts.forEach((code, count) ->
                breakdown.add(new PlanSubscriptionCount(code, code, count)));
        return breakdown;
    }

    /**
     * The legacy purchase column is floating point. The repository casts every row
     * to a two-decimal PostgreSQL numeric before summing, which gives the dashboard a
     * deterministic cent total. A versioned schema migration is still required to
     * make prices exact throughout catalogue, cart, checkout, and reporting flows.
     */
    private BigDecimal completedGmv() {
        BigDecimal total = purchaseRepository.sumRoundedTotalPriceByStatus(SETTLED.name());
        return total == null ? BigDecimal.ZERO : total;
    }
}
