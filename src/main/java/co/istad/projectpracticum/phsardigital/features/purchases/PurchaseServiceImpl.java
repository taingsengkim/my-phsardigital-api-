package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.address.Address;
import co.istad.projectpracticum.phsardigital.features.address.AddressService;
import co.istad.projectpracticum.phsardigital.features.cart.Cart;
import co.istad.projectpracticum.phsardigital.features.cart.CartItem;
import co.istad.projectpracticum.phsardigital.features.cart.CartRepository;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingAvailability;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.*;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final CartRepository cartRepository;
    private final ListingRepository listingRepository;
    private final PurchaseMapper purchaseMapper;
    private final SellerAccessGuard sellerAccessGuard;
    private final AddressService addressService;
    private final ListingAvailability listingAvailability;

    @Override
    @Transactional
    public PurchaseResponse checkout(String sellerId, CheckoutRequest request) {
        String buyerId = AuthUtils.extractUserId();

        // Suspending a shop stops it selling, not just posting. Without this the
        // storefront stayed open: the listings are still ACTIVE, so only the shop's
        // own flag says it may not trade.
        sellerAccessGuard.requireActiveSeller(sellerId);

        Cart cart = cartRepository
                .findByBuyerIdAndSellerProfile_SellerId(buyerId, sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No cart for this shop."));

        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your cart is empty.");
        }

        Purchase purchase = new Purchase();
        purchase.setBuyerId(buyerId);
        purchase.setSellerProfile(cart.getSellerProfile());
        purchase.setStatus(PurchaseStatus.PENDING);
        applyDelivery(purchase, request, buyerId);
        purchase.setNote(request.note());

        double total = 0.0;
        for (CartItem cartItem : cart.getItems()) {
            Listing listing = cartItem.getListing();

            listingAvailability.requireBuyable(listing);
            // soft check, real reservation happens at confirm
            if (listing.getStockQty() < cartItem.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not enough stock for: " + listing.getTitle()
                                + " (available " + listing.getStockQty() + ")");
            }

            // Both are snapshotted: the sale can end tomorrow, and the order has to keep
            // saying what was charged and what it would have cost.
            double unitPrice = listing.effectivePrice();

            PurchaseItem item = new PurchaseItem();
            item.setPurchase(purchase);
            item.setListing(listing);
            item.setQuantity(cartItem.getQuantity());
            item.setUnitPrice(unitPrice);
            item.setUnitFullPrice(listing.getFullPrice());
            purchase.getItems().add(item);

            total += unitPrice * cartItem.getQuantity();
        }

        purchase.setTotalPrice(total);
        Purchase saved = purchaseRepository.save(purchase);

        // empty this shop's cart
        cartRepository.delete(cart);

        return purchaseMapper.toResponse(saved);
    }

    // confirm -> decrement stock
    @Override
    @Transactional
    public PurchaseResponse confirm(UUID uuid) {
        Purchase purchase = getForSeller(uuid);

        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only PENDING orders can be confirmed.");
        }

        for (PurchaseItem item : lockOrdered(purchase)) {
            Listing listing = lockListing(item);
            if (listing.getStockQty() < item.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not enough stock to confirm: " + listing.getTitle());
            }
            listing.setStockQty(listing.getStockQty() - item.getQuantity());
            listing.setSold(listing.getSold() + item.getQuantity());
            // Stock depletion may refine a sellable listing to SOLD_OUT, but it must
            // never erase a stronger lifecycle state. In particular, an order placed
            // before moderation must not turn SUSPENDED into a public status when the
            // seller confirms it; restore will inspect the new stock and choose the
            // truthful public state later.
            if (listing.getStockQty() == 0
                    && listing.getStatus() == ListingStatus.ACTIVE) {
                listing.setStatus(ListingStatus.SOLD_OUT);
            }
            listingRepository.save(listing);
        }

        purchase.setStatus(PurchaseStatus.CONFIRMED);
        return purchaseMapper.toResponse(purchaseRepository.save(purchase));
    }

    @Override
    @Transactional
    public PurchaseResponse complete(UUID uuid) {
        Purchase purchase = getForSeller(uuid);
        if (purchase.getStatus() != PurchaseStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only CONFIRMED orders can be completed.");
        }
        purchase.setStatus(PurchaseStatus.COMPLETED);
        return purchaseMapper.toResponse(purchase);
    }

    @Override
    @Transactional
    public PurchaseResponse cancel(UUID uuid) {
        String userId = AuthUtils.extractUserId();
        Purchase purchase = purchaseRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase not found."));

        boolean isBuyer  = purchase.getBuyerId().equals(userId);
        boolean isSeller = purchase.getSellerProfile().getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order.");
        }
        if (purchase.getStatus() == PurchaseStatus.COMPLETED
                || purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order cannot be cancelled in its current state.");
        }

        // give stock back only if it was actually reserved
        if (purchase.getStatus() == PurchaseStatus.CONFIRMED) {
            for (PurchaseItem item : lockOrdered(purchase)) {
                Listing listing = lockListing(item);
                listing.setStockQty(listing.getStockQty() + item.getQuantity());
                listing.setSold(listing.getSold() - item.getQuantity());
                if (listing.getStatus() == ListingStatus.SOLD_OUT && listing.getStockQty() > 0) {
                    listing.setStatus(ListingStatus.ACTIVE);
                }
                listingRepository.save(listing);
            }
        }

        purchase.setStatus(PurchaseStatus.CANCELLED);
        return purchaseMapper.toResponse(purchase);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseResponse> findMyPurchases(PurchaseStatus status, int pageNumber, int pageSize) {
        String buyerId = AuthUtils.extractUserId();
        PageRequest pageable = PageRequest.of(pageNumber, pageSize);

        Page<Purchase> purchases = status == null
                ? purchaseRepository.findByBuyerId(buyerId, pageable)
                : purchaseRepository.findByBuyerIdAndStatus(buyerId, status, pageable);
        return purchases.map(purchaseMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseResponse findMyPurchaseByUuid(UUID uuid) {
        String buyerId = AuthUtils.extractUserId();
        Purchase purchase = purchaseRepository.findByUuidAndBuyerId(uuid, buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase not found."));
        return purchaseMapper.toResponse(purchase);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseResponse> findSellerOrders(PurchaseStatus status, int pageNumber, int pageSize) {
        String sellerId = AuthUtils.extractUserId();
        PageRequest pageable = PageRequest.of(pageNumber, pageSize);

        // Unfiltered is the order history; filtered to PENDING is the work queue. The
        // two answer different questions and the seller needs the second one far more.
        Page<Purchase> orders = status == null
                ? purchaseRepository.findBySellerProfile_SellerId(sellerId, pageable)
                : purchaseRepository.findBySellerProfile_SellerIdAndStatus(sellerId, status, pageable);
        return orders.map(purchaseMapper::toResponse);
    }

    /**
     * Copies the delivery details onto the order, from a saved address when one was
     * named and from the free-text field otherwise.
     *
     * <p>Requiring one or the other is the point: {@code shippingAddress} carried no
     * validation at all, so an order could be placed with none and reach a seller who
     * had no way to deliver it.
     */
    private void applyDelivery(Purchase purchase, CheckoutRequest request, String buyerId) {
        if (request.addressId() != null) {
            Address address = addressService.requireOwned(request.addressId(), buyerId);
            purchase.setShippingAddress(format(address));
            purchase.setRecipientName(address.getRecipient());
            purchase.setRecipientPhone(address.getPhone());
            return;
        }

        if (request.shippingAddress() == null || request.shippingAddress().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A delivery address is required. Send addressId for a saved address, "
                            + "or shippingAddress for a one-off.");
        }
        purchase.setShippingAddress(request.shippingAddress());
    }

    /** Flattens a saved address into the single line the order stores. */
    private String format(Address address) {
        return Stream.of(address.getLine1(), address.getLine2(), address.getCity(), address.getProvince())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
    }

    /**
     * The order's items, sorted by listing id, so every transaction takes its row
     * locks in the same sequence. Two orders sharing two listings would otherwise be
     * able to grab one each and wait forever on the other.
     */
    private List<PurchaseItem> lockOrdered(Purchase purchase) {
        return purchase.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getListing().getUuid()))
                .toList();
    }

    /**
     * Re-reads the listing {@code FOR UPDATE}. The instance hanging off the item was
     * loaded without a lock, so checking stock on it decides nothing.
     */
    private Listing lockListing(PurchaseItem item) {
        UUID listingUuid = item.getListing().getUuid();
        return listingRepository.findByUuidForUpdate(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Listing no longer exists: " + item.getListing().getTitle()));
    }

    private Purchase getForSeller(UUID uuid) {
        String sellerId = AuthUtils.extractUserId();
        Purchase purchase = purchaseRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase not found."));
        if (!purchase.getSellerProfile().getSellerId().equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order.");
        }
        return purchase;
    }

}
