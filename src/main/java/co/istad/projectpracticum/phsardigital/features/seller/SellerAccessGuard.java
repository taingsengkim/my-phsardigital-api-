package co.istad.projectpracticum.phsardigital.features.seller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The single definition of "this seller is allowed to trade".
 *
 * <p>Holding the {@code SELLER} realm role is not the same thing. The role is
 * granted once at approval and never revoked, so a shop suspended afterwards would
 * keep every permission the role carries. {@code SellerProfile.isActive} is the
 * flag that can be turned off — it was written at approval and then read nowhere,
 * which made suspension a no-op. Every write path a seller reaches goes through
 * here so that stays impossible to forget.
 */
@Component
@RequiredArgsConstructor
public class SellerAccessGuard {

    private final SellerRepository sellerRepository;
    private final EntityManager entityManager;

    /**
     * @param sellerId the caller's Keycloak subject
     * @return their profile, guaranteed active
     * @throws ResponseStatusException 404 when there is no seller profile, 403 when
     *         the shop is suspended
     */
    public SellerProfile requireActiveSeller(String sellerId) {
        SellerProfile profile = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Seller profile not found. Please complete seller registration first."));

        return requireActive(profile);
    }

    /**
     * The active check used when accepting money or inventory obligations. The lock is
     * shared, not exclusive: trading only reads {@code isActive}, so concurrent orders
     * for one shop may proceed together while an administrator's suspension still has
     * to wait for them and is therefore ordered against them.
     *
     * <p>Refreshing under the lock is intentional: the same seller may already be
     * present in the persistence context through an eagerly loaded order or listing,
     * and that copy predates the lock.
     */
    public SellerProfile requireActiveSellerForTrade(String sellerId) {
        SellerProfile profile = sellerRepository.findByIdForShare(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Seller profile not found. Please complete seller registration first."));
        entityManager.refresh(profile, LockModeType.PESSIMISTIC_READ);
        return requireActive(profile);
    }

    private SellerProfile requireActive(SellerProfile profile) {
        if (!Boolean.TRUE.equals(profile.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, suspensionMessage(profile));
        }
        return profile;
    }

    /**
     * Why the shop cannot trade, told to the shop itself — a seller discovers the
     * suspension by trying to post, so this is where they should learn the reason.
     * Safe to include because the message only ever reaches the shop's own owner.
     *
     * <p>The fallback covers a profile deactivated by something other than
     * {@code AdminSellerService}, which records a reason every time.
     */
    private String suspensionMessage(SellerProfile profile) {
        String reason = profile.getSuspensionReason();
        if (reason == null || reason.isBlank()) {
            return "This shop is not active. Contact support if you believe this is a mistake.";
        }
        return "This shop has been suspended by an administrator and cannot trade. Reason: " + reason;
    }

    /** Whether the caller has a seller profile at all, active or not. */
    public boolean isSeller(String userId) {
        return sellerRepository.existsById(userId);
    }
}
