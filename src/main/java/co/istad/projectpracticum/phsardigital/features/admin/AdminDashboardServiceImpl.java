package co.istad.projectpracticum.phsardigital.features.admin;

import co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfileRepository;
import co.istad.projectpracticum.phsardigital.features.seller.application.ApplicationStatus;
import co.istad.projectpracticum.phsardigital.features.seller.application.SellerApplicationRepository;
import co.istad.projectpracticum.phsardigital.features.subscription.SellerSubscriptionRepository;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlan;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionStatus;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    /** The only order state whose money has actually changed hands. */
    private static final PurchaseStatus SETTLED = PurchaseStatus.COMPLETED;

    private final UserProfileRepository userRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final ListingRepository listingRepository;
    private final SellerApplicationRepository applicationRepository;
    private final PurchaseRepository purchaseRepository;
    private final SellerSubscriptionRepository subscriptionRepository;

    /** One transaction around the lot, so the cards cannot contradict each other. */
    @Override
    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary() {
        LocalDateTime now = LocalDateTime.now();
        Map<SubscriptionPlan, Long> byPlan = activeSubscriptionsByPlan(now);

        return new AdminDashboardSummaryResponse(
                userRepository.count(),
                sellerProfileRepository.count(),
                sellerProfileRepository.countByIsActiveTrue(),
                listingRepository.count(),
                listingRepository.countByStatus(ListingStatus.ACTIVE),
                applicationRepository.countByStatus(ApplicationStatus.PENDING),
                purchaseRepository.countByStatus(SETTLED),
                money(purchaseRepository.sumTotalPriceByStatus(SETTLED)),
                byPlan.values().stream().mapToLong(Long::longValue).sum(),
                byPlan,
                now);
    }

    /**
     * Seeded with every plan, so one dropping to zero subscribers stays in the
     * response instead of disappearing from the chart.
     */
    private Map<SubscriptionPlan, Long> activeSubscriptionsByPlan(LocalDateTime now) {
        Map<SubscriptionPlan, Long> counts = new EnumMap<>(SubscriptionPlan.class);
        for (SubscriptionPlan plan : SubscriptionPlan.values()) {
            counts.put(plan, 0L);
        }

        List<Object[]> rows = subscriptionRepository.countActiveByPlan(SubscriptionStatus.ACTIVE, now);
        for (Object[] row : rows) {
            counts.put((SubscriptionPlan) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * {@code Purchase.totalPrice} is a {@code Double}, so the raw sum drifts —
     * rounding here papers over a column that should be {@code BigDecimal}.
     */
    private static BigDecimal money(Double total) {
        return BigDecimal.valueOf(total == null ? 0d : total)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
