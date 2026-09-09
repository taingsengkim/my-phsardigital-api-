package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import java.math.BigDecimal;

/**
 * How many orders sit in one state and what they are worth.
 *
 * <p>The value is carried alongside the count because they answer different questions:
 * thirty pending orders worth five dollars each is a quiet morning, and thirty worth two
 * thousand each is something an administrator wants to look at today.
 */
public record AdminPurchaseStatusTotalsResponse(
        long orderCount,
        BigDecimal value
) {
}
