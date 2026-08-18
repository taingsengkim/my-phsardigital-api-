package co.istad.projectpracticum.phsardigital.features.listings;

/**
 * Why a listing was suggested.
 *
 * <p>Published rather than kept internal so the storefront can label the strip honestly
 * — "Bought together" and "More from this shop" are different promises to a buyer, and
 * a single unexplained row of products cannot make either.
 *
 * <p>Declared in the order the tiers are filled: a listing that qualifies under more
 * than one reason is credited to the strongest.
 */
public enum RelatedReason {

    /** Ordered in the same basket as this listing, by somebody who went through with it. */
    BOUGHT_TOGETHER,

    /** The same category, across the whole marketplace, at a comparable price. */
    SAME_CATEGORY,

    /** Anything else this shop is selling. */
    SAME_SHOP
}
