package co.istad.projectpracticum.phsardigital.features.reports;

/**
 * Where a report has got to.
 *
 * <p>Deliberately only three. A queue with an "in progress" state needs somebody to
 * remember to leave it, and a report stuck there looks handled while nothing has
 * happened; open or closed is a distinction that cannot rot.
 */
public enum ReportStatus {

    /** Filed, and waiting for an admin. */
    OPEN,

    /** Looked at, and something was done about it. */
    RESOLVED,

    /** Looked at, and nothing needed doing. */
    DISMISSED
}
