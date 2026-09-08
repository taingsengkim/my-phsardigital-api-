package co.istad.projectpracticum.phsardigital.features.reports;

/**
 * What kind of thing a report is about.
 *
 * <p>A report names its target by type and id rather than by a foreign key, because
 * three different tables can be reported and a column cannot point at all of them. The
 * cost is that nothing stops a report naming something that has since been deleted;
 * {@code ReportTargetResolver} checks the target exists when the report is filed, and
 * the label snapshotted alongside it keeps the row readable afterwards either way.
 */
public enum ReportTargetType {

    /** A product. {@code targetId} is the listing's UUID. */
    LISTING,

    /** A shop. {@code targetId} is the seller's id — a Keycloak subject, not a UUID. */
    SELLER,

    /** A review left on a product. {@code targetId} is the review's UUID. */
    REVIEW
}
