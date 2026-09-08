package co.istad.projectpracticum.phsardigital.features.reports;

/**
 * Why somebody reported something.
 *
 * <p>One list across all three target types rather than one per type. A shorter list
 * that fits every target is answered honestly; a long list split by type invites the
 * reporter to hunt for the closest match and pick wrongly, and the admin reads the note
 * regardless.
 */
public enum ReportReason {

    /** Illegal, or not allowed to be sold here at all. */
    PROHIBITED,

    /** Passed off as a brand it is not. */
    COUNTERFEIT,

    /** The photos, price or description do not describe what is being sold. */
    MISLEADING,

    /** An attempt to take money without delivering. */
    SCAM,

    /** Obscene, violent, or otherwise not fit to be on the page. */
    OFFENSIVE,

    /** Repetitive or off-topic postings. */
    SPAM,

    /** Aimed at a person rather than about a product. */
    HARASSMENT,

    /**
     * Anything the list above does not cover. The note is required with this one — a
     * report reading only "other" tells the admin nothing they can act on.
     */
    OTHER
}
