package co.istad.projectpracticum.phsardigital.features.seller;

/**
 * The moderation states an admin filters the shop list by.
 *
 * <p>{@code isActive} alone cannot express this. It starts false and turns true only
 * on approval, so a shop that has never traded and a shop an admin took down are both
 * {@code isActive = false}. Only the recorded suspension separates them, which is why
 * {@link #SUSPENDED} keys off {@code suspendedAt} rather than the flag.
 */
public enum AdminSellerStatus {

    /** Trading. */
    ACTIVE,

    /** Taken down by an admin, and still carrying the reason why. */
    SUSPENDED,

    /** Not trading and never suspended — approved but switched off, or not yet approved. */
    INACTIVE
}
