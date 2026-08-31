package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.purchases.dto.CheckoutRequest;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.SellerOrderSummaryResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PurchaseService {

    /**
     * Buyer checks out one shop's cart. The cart's UUID becomes the order id, so
     * retrying the same request returns the first result instead of ordering twice.
     */
    PurchaseResponse checkout(String sellerId, CheckoutRequest request);

    /**
     * Buyer: my orders.
     *
     * @param status optional filter; null for the full history
     */
    Page<PurchaseResponse> findMyPurchases(PurchaseStatus status, int pageNumber, int pageSize);

    PurchaseResponse findMyPurchaseByUuid(UUID uuid);

    /**
     * Seller: orders on my shop.
     *
     * @param status optional filter; pass {@code PENDING} for the orders awaiting a
     *               decision, or null for the full history
     */
    /**
     * The calling shop's orders.
     *
     * @param search free text matched against the order id, the recipient's name and
     *               phone, and the titles of the products in the order. Null or blank
     *               lists everything.
     */
    Page<PurchaseResponse> findSellerOrders(PurchaseStatus status, String search,
                                            int pageNumber, int pageSize);

    /** Order counts per state and takings, for the shop's order dashboard. */
    SellerOrderSummaryResponse summariseMyOrders();

    /** Seller confirms -> DECREMENTS STOCK. */
    PurchaseResponse confirm(UUID uuid);

    /** Seller marks delivered & paid. */
    PurchaseResponse complete(UUID uuid);

    /** Cancel; restocks if it was CONFIRMED. */
    PurchaseResponse cancel(UUID uuid);
}
