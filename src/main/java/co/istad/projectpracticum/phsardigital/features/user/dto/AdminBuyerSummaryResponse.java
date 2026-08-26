package co.istad.projectpracticum.phsardigital.features.user.dto;

/**
 * The counters above the admin buyer table.
 *
 * <p>{@code total} counts every buyer account, so it is not the sum of the three
 * moderation states below — {@code PENDING} and {@code REJECTED} accounts are counted
 * in the total and have no card of their own.
 */
public record AdminBuyerSummaryResponse(
        long total,
        long active,
        long suspended,
        long banned
) {
}
