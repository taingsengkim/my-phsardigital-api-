package co.istad.projectpracticum.phsardigital.features.purchases;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * What the admin orders table is being narrowed by. Every field is optional; all of them
 * null means "every order".
 *
 * <p>A record rather than eight loose parameters, so the CSV export can be handed the
 * very same object the page was built from — an export that quietly applies different
 * filters from the table above it is a reporting bug nobody notices until the numbers
 * are in a meeting.
 *
 * @param from inclusive lower bound on when the order was placed
 * @param to   exclusive upper bound, so midnight-to-midnight is exactly one day
 */
public record AdminPurchaseFilter(
        Collection<PurchaseStatus> statuses,
        Collection<PurchaseChannel> channels,
        Collection<PaymentMethod> paymentMethods,
        String sellerId,
        String buyerId,
        LocalDateTime from,
        LocalDateTime to,
        String search
) {
}
