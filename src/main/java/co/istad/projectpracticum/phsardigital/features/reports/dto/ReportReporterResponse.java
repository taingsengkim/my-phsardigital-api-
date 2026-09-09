package co.istad.projectpracticum.phsardigital.features.reports.dto;

/**
 * Who filed the complaint, and how much weight it carries.
 *
 * <p>The identity fields are answered to administrators only. The reporter's own view of
 * their report — {@code GET /api/v1/reports/me} — is unchanged and carries none of this:
 * a reporter needs no dossier on themselves, and the person being reported never sees any
 * of it at all.
 *
 * @param isVerifiedBuyer whether this person completed an order for the reported thing.
 *                        True is strong evidence the complaint is first-hand. False is
 *                        <em>not</em> evidence of bad faith — somebody may report a
 *                        counterfeit they were about to buy, or a review they merely
 *                        read. Always null for a reported review, where the question does
 *                        not apply.
 * @param orderId         the completed order behind that mark, so an admin can open the
 *                        transaction being described. Null whenever
 *                        {@code isVerifiedBuyer} is not true.
 * @param totalReportsFiled how many complaints this person has ever filed, including this
 *                        one. High is ambiguous on its own — a diligent shopper and
 *                        somebody harassing a rival both score highly — so it is offered
 *                        as context, not as a verdict.
 */
public record ReportReporterResponse(
        String userId,
        String name,
        String email,
        String phone,
        Boolean isVerifiedBuyer,
        String orderId,
        long totalReportsFiled
) {
}
