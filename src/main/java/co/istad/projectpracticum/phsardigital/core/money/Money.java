package co.istad.projectpracticum.phsardigital.core.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Every amount the API stores or returns is scaled to 2 decimal places, half-up, so a
 * total is the same number wherever it is read back.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    /** Null-safe normalisation to the stored scale. */
    public static BigDecimal of(BigDecimal amount) {
        return amount == null ? null : amount.setScale(SCALE, ROUNDING);
    }

    /** A line total: unit price times quantity, at the stored scale. */
    public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null) {
            return ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal add(BigDecimal left, BigDecimal right) {
        return nullToZero(left).add(nullToZero(right)).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return nullToZero(left).subtract(nullToZero(right)).setScale(SCALE, ROUNDING);
    }

    /** Compares by value, so 10.00 and 10.0 are the same amount. */
    public static boolean isLessThan(BigDecimal left, BigDecimal right) {
        return nullToZero(left).compareTo(nullToZero(right)) < 0;
    }

    public static boolean isNegative(BigDecimal amount) {
        return nullToZero(amount).signum() < 0;
    }

    private static BigDecimal nullToZero(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }
}
