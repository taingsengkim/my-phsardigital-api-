package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.address.Address;
import co.istad.projectpracticum.phsardigital.features.address.AddressPhoto;
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
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
    private final UserProfileRepository userProfileRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public PurchaseResponse checkout(String sellerId, CheckoutRequest request) {
        String buyerId = AuthUtils.extractUserId();
        // The cart's UUID is the idempotency key: it identifies exactly the basket the
        // buyer was shown, and a cart is deleted once it becomes an order, so the key
        // is never reused for a second purchase.
        UUID cartUuid = request.cartUuid();

        if (buyerId.equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot place an order with your own shop.");
        }
        validateDeliveryChoice(request);

        // There is no order row before the first request, so serialize creation on the
        // buyer. A retry then waits for the original transaction and can replay its
        // purchase instead of finding a consumed cart (or consuming a newer cart).
        UserProfile buyer = userProfileRepository.findByIdForCommerceLock(buyerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Buyer profile not found."));

        Purchase replay = purchaseRepository.findById(cartUuid).orElse(null);
        if (replay != null) {
            if (!replay.getBuyerId().equals(buyerId)
                    || !replay.getSellerProfile().getSellerId().equals(sellerId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This cart was already checked out under a different buyer or shop.");
            }
            assertReplayCompatible(replay, request, buyerId);
            return purchaseMapper.toResponse(replay);
        }

        // A shared lock on the shop makes the active check and an administrator's
        // suspension one ordered decision: the checkout either commits before the
        // suspension or sees it and refuses the new order. Because it is shared rather
        // than exclusive, concurrent checkouts for one shop still run in parallel.
        sellerAccessGuard.requireActiveSellerForTrade(sellerId);

        // Checkout and every cart edit take the same parent-row lock, so a cart cannot
        // change while its immutable order snapshot is being built.
        Cart cart = cartRepository
                .findByBuyerIdAndSellerIdForUpdate(buyerId, sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No cart for this shop."));

        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your cart is empty.");
        }
        if (!Objects.equals(cart.getUuid(), cartUuid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your cart changed before checkout. Review it and try again with its current UUID.");
        }

        Purchase purchase = new Purchase();
        purchase.setUuid(cartUuid);
        purchase.setBuyerId(buyerId);
        purchase.setSellerProfile(cart.getSellerProfile());
        purchase.setStatus(PurchaseStatus.PENDING);
        applyDelivery(purchase, request, buyerId, buyer);
        purchase.setNote(trimToNull(request.note()));

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
        // Lock the order before inspecting its state. Listing locks alone cannot stop
        // two requests that both observed the same stale PENDING purchase from applying
        // the inventory mutation twice.
        Purchase purchase = getForSellerForUpdate(uuid);

        // Confirmation has already succeeded in both states. A delayed retry must not
        // decrement inventory again merely because the order advanced meanwhile.
        if (purchase.getStatus() == PurchaseStatus.CONFIRMED
                || purchase.getStatus() == PurchaseStatus.COMPLETED) {
            return purchaseMapper.toResponse(purchase);
        }

        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only PENDING orders can be confirmed.");
        }

        // A suspended shop may finish or cancel obligations it already accepted, but
        // it may not accept a new pending order while trading is disabled.
        sellerAccessGuard.requireActiveSellerForTrade(
                purchase.getSellerProfile().getSellerId());

        for (PurchaseItem item : lockOrdered(purchase)) {
            Listing listing = lockListing(item);
            // Re-check after taking the listing lock. Availability may have changed
            // after checkout (moderation, category deactivation, seller suspension or
            // another order consuming the remaining stock).
            listingAvailability.requireBuyable(listing);
            if (listing.getStockQty() < item.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not enough stock to confirm: " + listing.getTitle());
            }
            listing.setStockQty(listing.getStockQty() - item.getQuantity());
            listing.setSold(listing.getSold() + item.getQuantity());
            // Stock depletion refines the currently sellable state to SOLD_OUT. Keep
            // the status guard as defense in depth: inventory accounting must never
            // overwrite a stronger moderation/lifecycle state.
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
        Purchase purchase = getForSellerForUpdate(uuid);
        if (purchase.getStatus() == PurchaseStatus.COMPLETED) {
            return purchaseMapper.toResponse(purchase);
        }
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
        Purchase purchase = purchaseRepository.findByUuidForUpdate(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase not found."));

        boolean isBuyer  = purchase.getBuyerId().equals(userId);
        boolean isSeller = purchase.getSellerProfile().getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order.");
        }

        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            return purchaseMapper.toResponse(purchase);
        }
        if (purchase.getStatus() == PurchaseStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order cannot be cancelled in its current state.");
        }

        if (purchase.getStatus() == PurchaseStatus.CONFIRMED && isBuyer && !isSeller) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This order has already been accepted by the seller and can no longer "
                            + "be cancelled by the buyer. Contact the seller for help.");
        }

        // give stock back only if it was actually reserved
        if (purchase.getStatus() == PurchaseStatus.CONFIRMED) {
            for (PurchaseItem item : lockOrdered(purchase)) {
                Listing listing = lockListing(item);
                if (listing.getSold() == null || listing.getSold() < item.getQuantity()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Cannot cancel because the inventory history is inconsistent for: "
                                    + listing.getTitle());
                }
                try {
                    listing.setStockQty(Math.addExact(
                            listing.getStockQty(), item.getQuantity()));
                } catch (ArithmeticException exception) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Cannot restore stock because its quantity would overflow for: "
                                    + listing.getTitle(), exception);
                }
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
        PageRequest pageable = purchasePage(pageNumber, pageSize);

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
        PageRequest pageable = purchasePage(pageNumber, pageSize);

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
    private void applyDelivery(Purchase purchase,
                               CheckoutRequest request,
                               String buyerId,
                               UserProfile buyer) {
        // checkout() has already validated the delivery choice before taking any lock.
        boolean hasSavedAddress = request.addressId() != null;

        String savedRecipient = null;
        String savedPhone = null;
        if (hasSavedAddress) {
            Address address = addressService.requireOwned(request.addressId(), buyerId);
            String addressText = trimToNull(format(address));
            if (addressText == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The saved delivery address is empty. Update it before checkout.");
            }
            purchase.setShippingAddress(addressText);
            savedRecipient = address.getRecipient();
            savedPhone = address.getPhone();
            copyLandmarkPhotos(purchase, address);
        } else {
            purchase.setShippingAddress(request.shippingAddress().trim());
        }

        String recipient = firstNonBlank(
                request.recipientName(), savedRecipient, buyer.getFullName());
        String phone = firstNonBlank(
                request.recipientPhone(), savedPhone, buyer.getPhone());
        if (recipient == null || phone == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A recipient name and phone are required for delivery. Add them "
                            + "to this checkout, the saved address, or your profile.");
        }
        purchase.setRecipientName(recipient);
        purchase.setRecipientPhone(phone);
    }

    /**
     * Copies the address's landmark shots onto the order, for the same reason the
     * address text is copied: the buyer may edit or delete the saved address, and what
     * the seller was shown when the order arrived must not change underneath them.
     */
    private void copyLandmarkPhotos(Purchase purchase, Address address) {
        purchase.getDeliveryPhotos().clear();
        for (AddressPhoto source : address.getPhotos()) {
            PurchaseDeliveryPhoto photo = new PurchaseDeliveryPhoto();
            photo.setPurchase(purchase);
            photo.setFile(source.getFile());
            photo.setCaption(source.getCaption());
            photo.setSortOrder(source.getSortOrder());
            purchase.getDeliveryPhotos().add(photo);
        }
    }

    private void validateDeliveryChoice(CheckoutRequest request) {
        boolean hasSavedAddress = request.addressId() != null;
        boolean hasOneOffAddress = request.shippingAddress() != null
                && !request.shippingAddress().isBlank();
        if (hasSavedAddress && hasOneOffAddress) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose either addressId or shippingAddress, not both.");
        }
        if (!hasSavedAddress && !hasOneOffAddress) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A delivery address is required. Send addressId for a saved address, "
                            + "or shippingAddress for a one-off.");
        }
    }

    private void assertReplayCompatible(Purchase purchase, CheckoutRequest request, String buyerId) {
        boolean changed = !Objects.equals(trimToNull(request.note()), purchase.getNote());
        // A saved address was flattened into the order, so a replay naming one is
        // compared against the text that address resolves to now. Without this, only
        // the one-off shippingAddress was checked and a changed addressId slipped by.
        String requestedAddress = request.addressId() != null
                ? trimToNull(format(addressService.requireOwned(request.addressId(), buyerId)))
                : trimToNull(request.shippingAddress());
        String requestedRecipient = trimToNull(request.recipientName());
        String requestedPhone = trimToNull(request.recipientPhone());
        if (requestedAddress != null) {
            changed |= !Objects.equals(requestedAddress, purchase.getShippingAddress());
        }
        if (requestedRecipient != null) {
            changed |= !Objects.equals(requestedRecipient, purchase.getRecipientName());
        }
        if (requestedPhone != null) {
            changed |= !Objects.equals(requestedPhone, purchase.getRecipientPhone());
        }
        if (changed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This cart was already checked out with different checkout details.");
        }
    }

    /** Flattens a saved address into the single line the order stores. */
    private String format(Address address) {
        return Stream.of(address.getLine1(), address.getLine2(), address.getCity(), address.getProvince())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String candidate = trimToNull(value);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static PageRequest purchasePage(int pageNumber, int pageSize) {
        Sort newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("uuid"));
        return PageRequest.of(pageNumber, pageSize, newestFirst);
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
        Listing listing = listingRepository.findByUuidForUpdate(listingUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Listing no longer exists: " + item.getListing().getTitle()));
        // PurchaseItem may already have put this listing in the persistence context.
        // Refresh explicitly after locking so inventory and availability checks never
        // run against that pre-lock snapshot.
        entityManager.refresh(listing, LockModeType.PESSIMISTIC_WRITE);
        return listing;
    }

    private Purchase getForSellerForUpdate(UUID uuid) {
        String sellerId = AuthUtils.extractUserId();
        Purchase purchase = purchaseRepository.findByUuidForUpdate(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase not found."));
        if (!purchase.getSellerProfile().getSellerId().equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order.");
        }
        return purchase;
    }

}
