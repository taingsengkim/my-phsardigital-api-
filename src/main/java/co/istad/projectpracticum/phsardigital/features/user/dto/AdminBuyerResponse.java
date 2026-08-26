package co.istad.projectpracticum.phsardigital.features.user.dto;

import co.istad.projectpracticum.phsardigital.features.user.UserStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A buyer account as the admin buyer list shows it.
 *
 * <p>Separate from {@link AdminUserResponse}, which describes any account: this one
 * carries the commerce totals the buyer table has columns for, and the moderation
 * trail behind {@link #status}.
 *
 * @param totalOrders completed orders only — a pending order is not a purchase yet,
 *                    and a cancelled one never became one
 * @param totalSpent  what those orders came to, in the marketplace currency (USD),
 *                    always at two decimal places and never null
 */
public record AdminBuyerResponse(
        String id,
        String username,
        String fullName,
        String email,
        Boolean emailVerified,
        String phone,
        String avatarUrl,
        UserStatus status,
        String moderatedBy,
        LocalDateTime moderatedAt,
        String moderationReason,
        LocalDateTime joinedAt,
        long totalOrders,
        BigDecimal totalSpent
) {
}
