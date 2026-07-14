package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.cart.Cart;
import co.istad.projectpracticum.phsardigital.features.cart.CartItem;
import co.istad.projectpracticum.phsardigital.features.cart.CartRepository;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final CartRepository cartRepository;
    private final ListingRepository listingRepository;
    private final PurchaseMapper purchaseMapper;

    @Override
    @Transactional
    public PurchaseResponse checkout(String sellerId, CheckoutRequest request) {
        String buyerId = AuthUtils.extractUserId();

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
        purchase.setShippingAddress(request.shippingAddress());
        purchase.setNote(request.note());

        double total = 0.0;
        for (CartItem cartItem : cart.getItems()) {
            Listing listing = cartItem.getListing();

            if (listing.getStatus() != ListingStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Listing is no longer available: " + listing.getTitle());
            }
            // soft check, real reservation happens at confirm
            if (listing.getStockQty() < cartItem.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not enough stock for: " + listing.getTitle()
                                + " (available " + listing.getStockQty() + ")");
            }

            PurchaseItem item = new PurchaseItem();
            item.setPurchase(purchase);
            item.setListing(listing);
            item.setQuantity(cartItem.getQuantity());
            item.setUnitPrice(listing.getPrice()); //snapsot price
            purchase.getItems().add(item);

            total += listing.getPrice() * cartItem.getQuantity();
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

        for (PurchaseItem item : purchase.getItems()) {
            Listing listing = item.getListing();
            if (listing.getStockQty() < item.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not enough stock to confirm: " + listing.getTitle());
            }
            listing.setStockQty(listing.getStockQty() - item.getQuantity());
            listing.setSold(listing.getSold() + item.getQuantity());
            if (listing.getStockQty() == 0) {
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
            for (PurchaseItem item : purchase.getItems()) {
                Listing listing = item.getListing();
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
    public Page<PurchaseResponse> findMyPurchases(int pageNumber, int pageSize) {
        String buyerId = AuthUtils.extractUserId();
        return purchaseRepository.findByBuyerId(buyerId, PageRequest.of(pageNumber, pageSize))
                .map(purchaseMapper::toResponse)   ;
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
    public Page<PurchaseResponse> findSellerOrders(int pageNumber, int pageSize) {
        String sellerId = AuthUtils.extractUserId();
        return purchaseRepository
                .findBySellerProfile_SellerId(sellerId, PageRequest.of(pageNumber, pageSize))
                .map(purchaseMapper::toResponse);
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