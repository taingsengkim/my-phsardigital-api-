package co.istad.projectpracticum.phsardigital.features.seller;

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
