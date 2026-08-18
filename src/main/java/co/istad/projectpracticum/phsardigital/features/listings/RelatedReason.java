package co.istad.projectpracticum.phsardigital.features.listings;

/**
 * Why a listing was suggested, so the storefront can label the strip — "Bought
 * together" and "More from this shop" are different promises to a buyer.
 *
 * <p>Declared in the order the tiers are filled: a listing qualifying under more than
 * one reason is credited to the strongest.
 */
public enum RelatedReason {

    /** Ordered in the same basket as this listing, by somebody who went through with it. */
    BOUGHT_TOGETHER,

    /** The same category, across the whole marketplace, at a comparable price. */
    SAME_CATEGORY,

    /** Anything else this shop is selling. */
    SAME_SHOP
}
