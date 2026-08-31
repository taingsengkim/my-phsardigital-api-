package co.istad.projectpracticum.phsardigital.features.review.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * The star breakdown behind a rating — what a storefront or product page draws above
 * its review list.
 *
 * <p>Its own endpoint rather than more fields on the shop and listing responses. Those
 * two are embedded inside other payloads — a shop profile is nested in every single
 * review — so five more counters on each would be paid for on every page that mentions
 * a shop, to be drawn on the one page that shows the bars.
 *
 * @param averageRating to one decimal place, or <strong>null</strong> when nothing has
 *                      been reviewed. Null rather than 0.0, because an unreviewed shop
 *                      has no rating rather than the worst possible one, and a client
 *                      that renders 0.0 stars for a new shop libels it.
 * @param reviewCount   how many reviews the average is over
 * @param breakdown     count at each star, always five entries, five stars first,
 *                      including stars nobody has given
 */
public record ReviewSummaryResponse(
        Double averageRating,
        long reviewCount,
        List<StarCount> breakdown
) {

    /**
     * @param percentage share of all reviews at this star, one decimal place — the width
     *                   of the bar. Zero when there are no reviews at all, so a client
     *                   never divides by nothing.
     */
    public record StarCount(int stars, long count, BigDecimal percentage) {
    }

    /**
     * @param countsByStar how many reviews at each star, missing entries meaning none
     */
    public static ReviewSummaryResponse of(Map<Integer, Long> countsByStar) {
        long total = countsByStar.values().stream().mapToLong(Long::longValue).sum();

        List<StarCount> breakdown = java.util.stream.IntStream.rangeClosed(1, 5)
                .map(star -> 6 - star)
                .mapToObj(star -> new StarCount(
                        star,
                        countsByStar.getOrDefault(star, 0L),
                        share(countsByStar.getOrDefault(star, 0L), total)))
                .toList();

        return new ReviewSummaryResponse(average(countsByStar, total), total, breakdown);
    }

    /**
     * Averaged from the same counts the bars are drawn from, rather than queried
     * separately — two aggregates over one table can disagree if a review lands between
     * them, and a mean that contradicts its own breakdown is the kind of thing nobody
     * can explain afterwards.
     */
    private static Double average(Map<Integer, Long> countsByStar, long total) {
        if (total == 0) {
            return null;
        }
        long weighted = countsByStar.entrySet().stream()
                .mapToLong(entry -> (long) entry.getKey() * entry.getValue())
                .sum();
        return BigDecimal.valueOf(weighted)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static BigDecimal share(long count, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
