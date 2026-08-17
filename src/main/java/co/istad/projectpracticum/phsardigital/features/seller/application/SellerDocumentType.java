package co.istad.projectpracticum.phsardigital.features.seller.application;

import java.util.Set;

/**
 * The kinds of supporting document a seller application can carry.
 *
 * <p>An enum rather than the free-text string this used to be: the value decides
 * whether an application is complete enough to approve, so a typo like
 * {@code "buiness_license"} must be a 400 at submission time and not a silently
 * missing licence at review time.
 */
public enum SellerDocumentType {

    /** Government-issued identity document for the person behind the shop. */
    ID_CARD,

    /** The shop's business licence or registration certificate. */
    BUSINESS_LICENSE,

    /** Anything the applicant volunteers beyond what is required. */
    OTHER;

    /**
     * What an applicant must attach before an admin can approve them. Kept here
     * next to the enum so adding a required document is a one-line change that
     * cannot drift from the review check that reads it.
     */
    public static final Set<SellerDocumentType> REQUIRED_FOR_APPROVAL =
            Set.of(ID_CARD, BUSINESS_LICENSE);
}
