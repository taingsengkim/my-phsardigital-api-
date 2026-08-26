package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.seller.dto.AdminSellerResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
import org.springframework.data.domain.Page;

public interface AdminSellerService {

    /**
     * Every shop an admin can moderate, suspended ones included — the read side of
     * {@link #suspend} and {@link #restore}.
     *
     * <p>Deliberately not the public Top Sellers board: that one ranks shops by
     * settled orders in a window, so a shop with no completed sales is absent from it
     * entirely rather than listed with a zero.
     *
     * @param status filters by moderation state; null lists every shop
     * @param search case-insensitive substring of the business name; null or blank
     *               matches every shop
     */
    Page<AdminSellerResponse> list(AdminSellerStatus status, String search,
                                   int pageNumber, int pageSize);

    /**
     * Stops a shop trading. {@code SellerAccessGuard} already refuses an inactive
     * seller on every write path, so this is the switch for machinery that is
     * otherwise unreachable — nothing else in the codebase ever sets
     * {@code isActive} back to false.
     */
    AdminSellerResponse suspend(String sellerId, SuspendRequest request);

    AdminSellerResponse restore(String sellerId);
}
