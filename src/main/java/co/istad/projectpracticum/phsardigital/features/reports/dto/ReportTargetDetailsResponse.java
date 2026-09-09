package co.istad.projectpracticum.phsardigital.features.reports.dto;

import java.math.BigDecimal;

/**
 * The reported thing as it stands <em>now</em>, looked up live.
 *
 * <p>One shape covering all three target types rather than three, because the admin
 * drawer renders one panel and switching on a discriminator it already has
 * ({@code targetType}) is simpler than a polymorphic union. Fields that do not apply to
 * the type are null: a shop has no price, a review has no thumbnail.
 *
 * <p>The whole object is null when the target has been deleted. That is an ordinary
 * outcome, not an error — reports deliberately outlive what they name, which is why
 * {@code targetLabel} is snapshotted at filing. An admin closing a report about a listing
 * that has since been removed still needs to see the complaint, so the UI must render
 * {@code targetLabel} with a "no longer exists" note rather than assuming this is present.
 *
 * <p>There is no currency field because the system has no currency concept — every
 * amount in this API is in the one currency the marketplace trades in.
 *
 * @param currentStatus the live status of the thing: a {@code ListingStatus} for a
 *                      listing, {@code ACTIVE}/{@code SUSPENDED}/{@code INACTIVE} for a
 *                      shop, and null for a review, which has no status of its own
 * @param sellerStatus  the owning shop's standing, so an admin can see at a glance that
 *                      the shop behind a reported listing is already suspended
 * @param rating        1–5, on a reported review only
 * @param authorName    who wrote the reported review
 */
public record ReportTargetDetailsResponse(
        String title,
        String imageUrl,
        BigDecimal fullPrice,
        BigDecimal discountPrice,
        String currentStatus,
        String categoryName,
        String sellerId,
        String sellerName,
        String sellerStatus,
        Integer activeListingCount,
        Integer rating,
        String comment,
        String authorName
) {

    /** A reported product. */
    public static ReportTargetDetailsResponse listing(
            String title, String imageUrl, BigDecimal fullPrice, BigDecimal discountPrice,
            String currentStatus, String categoryName,
            String sellerId, String sellerName, String sellerStatus) {
        return new ReportTargetDetailsResponse(title, imageUrl, fullPrice, discountPrice,
                currentStatus, categoryName, sellerId, sellerName, sellerStatus,
                null, null, null, null);
    }

    /** A reported shop. */
    public static ReportTargetDetailsResponse seller(
            String businessName, String logoUrl, String sellerId, String sellerStatus,
            int activeListingCount) {
        return new ReportTargetDetailsResponse(businessName, logoUrl, null, null,
                sellerStatus, null, sellerId, businessName, sellerStatus,
                activeListingCount, null, null, null);
    }

    /** A reported review. */
    public static ReportTargetDetailsResponse review(
            Integer rating, String comment, String authorName, String imageUrl,
            String sellerId, String sellerName, String sellerStatus) {
        return new ReportTargetDetailsResponse(null, imageUrl, null, null, null, null,
                sellerId, sellerName, sellerStatus, null, rating, comment, authorName);
    }
}
