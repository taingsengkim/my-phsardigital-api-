package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.seller.dto.AdminSellerResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;

public interface AdminSellerService {

    /**
     * Stops a shop trading. {@code SellerAccessGuard} already refuses an inactive
     * seller on every write path, so this is the switch for machinery that is
     * otherwise unreachable — nothing else in the codebase ever sets
     * {@code isActive} back to false.
     */
    AdminSellerResponse suspend(String sellerId, SuspendRequest request);

    AdminSellerResponse restore(String sellerId);
}
