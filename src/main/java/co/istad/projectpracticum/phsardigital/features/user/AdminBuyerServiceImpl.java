package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.seller.SellerRepository;
import co.istad.projectpracticum.phsardigital.features.user.dto.AdminBuyerResponse;
import co.istad.projectpracticum.phsardigital.features.user.dto.AdminBuyerSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.user.dto.ModerateBuyerRequest;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBuyerServiceImpl implements AdminBuyerService {

    /** The only order state whose money has actually changed hands. */
    private static final PurchaseStatus SETTLED = PurchaseStatus.COMPLETED;

    /** The standings an admin can lift with {@link #restore}. */
    private static final Set<UserStatus> MODERATED =
            Set.of(UserStatus.SUSPENDED, UserStatus.BANNED);

    private final UserProfileRepository userProfileRepository;
    private final SellerRepository sellerRepository;
    private final PurchaseRepository purchaseRepository;
    private final UserProfileMapper userProfileMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminBuyerResponse> list(UserStatus status, String search,
                                         LocalDate joinedFrom, LocalDate joinedTo,
                                         int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(
                pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserProfile> page = userProfileRepository.findAll(
                matching(status, search, joinedFrom, joinedTo), pageable);
        if (page.isEmpty()) {
            return page.map(profile -> toResponse(profile, null));
        }

        // One aggregate query for the whole page rather than two per row.
        Map<String, OrderStats> stats = orderStatsFor(
                page.getContent().stream().map(UserProfile::getId).toList());
        return page.map(profile -> toResponse(profile, stats.get(profile.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminBuyerSummaryResponse summary() {
        Map<UserStatus, Long> counts = new EnumMap<>(UserStatus.class);
        long total = 0;
        for (Object[] row : userProfileRepository.countBuyersByStatus()) {
            long count = ((Number) row[1]).longValue();
            total += count;
            // A legacy row can hold no standing at all; it still counts toward the total.
            if (row[0] != null) {
                counts.put((UserStatus) row[0], count);
            }
        }

        return new AdminBuyerSummaryResponse(
                total,
                counts.getOrDefault(UserStatus.ACTIVE, 0L),
                counts.getOrDefault(UserStatus.SUSPENDED, 0L),
                counts.getOrDefault(UserStatus.BANNED, 0L));
    }

    @Override
    @Transactional
    public AdminBuyerResponse suspend(String userId, ModerateBuyerRequest request) {
        return moderate(userId, UserStatus.SUSPENDED, request.reason());
    }

    @Override
    @Transactional
    public AdminBuyerResponse ban(String userId, ModerateBuyerRequest request) {
        return moderate(userId, UserStatus.BANNED, request.reason());
    }

    @Override
    @Transactional
    public AdminBuyerResponse restore(String userId) {
        UserProfile profile = requireBuyer(userId);
        if (!MODERATED.contains(profile.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This account is not suspended or banned.");
        }

        profile.setStatus(UserStatus.ACTIVE);
        // Cleared rather than kept, so an account moderated twice does not answer with
        // the first reason still attached.
        profile.setModeratedBy(null);
        profile.setModeratedAt(null);
        profile.setModerationReason(null);

        log.info("Buyer {} restored by {}", userId, AuthUtils.extractUserId());
        return withStats(userProfileRepository.save(profile));
    }

    private AdminBuyerResponse moderate(String userId, UserStatus target, String reason) {
        UserProfile profile = requireBuyer(userId);
        if (profile.getStatus() == target) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This account is already " + target.name().toLowerCase(Locale.ROOT) + ".");
        }

        profile.setStatus(target);
        profile.setModeratedBy(AuthUtils.extractUserId());
        profile.setModeratedAt(LocalDateTime.now());
        profile.setModerationReason(reason);

        log.info("Buyer {} set to {} by {}", userId, target, profile.getModeratedBy());
        return withStats(userProfileRepository.save(profile));
    }

    /**
     * Takes the same exclusive lock the commerce paths use, so a ban cannot interleave
     * with a checkout that started before it.
     */
    private UserProfile requireBuyer(String userId) {
        UserProfile profile = userProfileRepository.findByIdForCommerceLock(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));

        // A shop owner is moderated through the seller endpoints, which also stop the
        // shop trading. Banning them here would leave their listings on sale.
        if (sellerRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This account owns a shop. Moderate it at /api/v1/admin/sellers.");
        }
        return profile;
    }

    private AdminBuyerResponse withStats(UserProfile profile) {
        return toResponse(profile, orderStatsFor(List.of(profile.getId())).get(profile.getId()));
    }

    private Map<String, OrderStats> orderStatsFor(List<String> buyerIds) {
        Map<String, OrderStats> stats = new HashMap<>();
        if (buyerIds.isEmpty()) {
            return stats;
        }
        for (Object[] row : purchaseRepository.orderStatsForBuyers(SETTLED.name(), buyerIds)) {
            stats.put((String) row[0], new OrderStats(
                    ((Number) row[1]).longValue(),
                    (BigDecimal) row[2]));
        }
        return stats;
    }

    private AdminBuyerResponse toResponse(UserProfile profile, OrderStats stats) {
        return new AdminBuyerResponse(
                profile.getId(),
                profile.getUsername(),
                profile.getFullName(),
                profile.getEmail(),
                profile.getEmailVerified(),
                profile.getPhone(),
                userProfileMapper.avatarUrl(profile),
                profile.getStatus(),
                profile.getModeratedBy(),
                profile.getModeratedAt(),
                profile.getModerationReason(),
                profile.getCreatedAt(),
                stats == null ? 0L : stats.orders(),
                // A buyer with no settled order has no aggregate row; the table still
                // wants a number in the column.
                stats == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                        : stats.spent().setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Buyers only, plus whichever of the three filters were supplied. An empty
     * conjunction is impossible here — the buyer rule always applies.
     */
    private static Specification<UserProfile> matching(UserStatus status, String search,
                                                       LocalDate joinedFrom, LocalDate joinedTo) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // No shop of their own — the marketplace's definition of a buyer.
            Subquery<Integer> shop = query.subquery(Integer.class);
            Root<SellerProfile> shopRoot = shop.from(SellerProfile.class);
            shop.select(builder.literal(1))
                    .where(builder.equal(shopRoot.get("sellerId"), root.get("id")));
            predicates.add(builder.not(builder.exists(shop)));

            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = likePattern(search);
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("fullName")), pattern, '\\'),
                        builder.like(builder.lower(root.get("email")), pattern, '\\'),
                        builder.like(builder.lower(root.get("phone")), pattern, '\\')));
            }
            if (joinedFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("createdAt"), joinedFrom.atStartOfDay()));
            }
            if (joinedTo != null) {
                // Exclusive upper bound on the next midnight, so the whole of joinedTo
                // is included whatever time of day an account was created.
                predicates.add(builder.lessThan(
                        root.get("createdAt"), joinedTo.plusDays(1).atStartOfDay()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Escaped so a search containing {@code %} or {@code _} is matched literally. */
    private static String likePattern(String search) {
        String escaped = search.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private record OrderStats(long orders, BigDecimal spent) {
    }
}
