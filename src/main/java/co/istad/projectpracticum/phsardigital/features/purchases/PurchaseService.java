package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.purchases.dto.CheckoutRequest;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PurchaseService {

    /** Buyer checks out ONE shop's cart. Snapshots price, creates PENDING order, empties that cart. */
    PurchaseResponse checkout(String sellerId, CheckoutRequest request);

    Page<PurchaseResponse> findMyPurchases(int pageNumber, int pageSize);

    PurchaseResponse findMyPurchaseByUuid(UUID uuid);

    /** Seller: orders on my shop. */
    Page<PurchaseResponse> findSellerOrders(int pageNumber, int pageSize);

    /** Seller confirms -> DECREMENTS STOCK. */
    PurchaseResponse confirm(UUID uuid);

    /** Seller marks delivered & paid. */
    PurchaseResponse complete(UUID uuid);

    /** Cancel; restocks if it was CONFIRMED. */
    PurchaseResponse cancel(UUID uuid);
}