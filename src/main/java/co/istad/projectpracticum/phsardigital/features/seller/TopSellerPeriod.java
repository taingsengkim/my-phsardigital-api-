package co.istad.projectpracticum.phsardigital.features.seller;

import java.time.LocalDateTime;

/**
 * How far back to count. A window rather than all-time by default, because an
 * all-time board ossifies — the shops that opened first stay on top of it forever and
 * a good new shop can never appear.
 */
public enum TopSellerPeriod {

    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    LAST_90_DAYS(90),
    ALL_TIME(null);

    private final Integer days;

    TopSellerPeriod(Integer days) {
        this.days = days;
    }

    /**
     * @return the cutoff to compare against. All-time answers a date old enough to
     *         include everything rather than null, so the queries can compare
     *         unconditionally instead of carrying a null-check branch each.
     */
    public LocalDateTime since(LocalDateTime now) {
        return days == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : now.minusDays(days);
    }
}
