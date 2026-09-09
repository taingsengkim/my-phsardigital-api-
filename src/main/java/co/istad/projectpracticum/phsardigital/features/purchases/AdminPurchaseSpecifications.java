package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The filters behind the admin orders table. Every one is optional, so asking for none
 * correctly returns every order.
 */
final class AdminPurchaseSpecifications {

    private AdminPurchaseSpecifications() {
    }

    static Specification<Purchase> matching(AdminPurchaseFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (filter.channels() != null && !filter.channels().isEmpty()) {
                predicates.add(root.get("channel").in(filter.channels()));
            }
            if (filter.paymentMethods() != null && !filter.paymentMethods().isEmpty()) {
                // Deliberately does not match online orders, whose method is null because
                // nobody confirmed how they were paid. Filtering by a payment method asks
                // which sales are *known* to have been settled that way.
                predicates.add(root.get("paymentMethod").in(filter.paymentMethods()));
            }
            if (filter.sellerId() != null && !filter.sellerId().isBlank()) {
                predicates.add(builder.equal(
                        root.get("sellerProfile").get("sellerId"), filter.sellerId().trim()));
            }
            if (filter.buyerId() != null && !filter.buyerId().isBlank()) {
                predicates.add(builder.equal(root.get("buyerId"), filter.buyerId().trim()));
            }
            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                // Half-open, so midnight-to-midnight is one whole day and an order at
                // 23:59:59.999 is not dropped by a rounded bound.
                predicates.add(builder.lessThan(root.get("createdAt"), filter.to()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                predicates.add(searchPredicate(root, query, builder, filter.search()));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Free text across what an administrator actually has to hand when somebody writes in
     * about an order: its id, who it is going to, their phone, the shop, and what was in
     * it.
     *
     * <p>Products and buyers are reached by subquery rather than by join, for two separate
     * reasons. The buyer has no association to join — {@code Purchase.buyerId} is a bare
     * string column. The items do, but joining them multiplies an order by its lines, so
     * an order of three products would appear three times and, worse, the page's total
     * count would be three times too large.
     */
    private static Predicate searchPredicate(Root<Purchase> root,
                                             CriteriaQuery<?> query,
                                             CriteriaBuilder builder,
                                             String search) {
        String pattern = likePattern(search);
        List<Predicate> matches = new ArrayList<>();

        matches.add(builder.like(builder.lower(root.get("uuid").as(String.class)), pattern, '\\'));
        matches.add(builder.like(builder.lower(root.get("recipientName")), pattern, '\\'));
        matches.add(builder.like(builder.lower(root.get("recipientPhone")), pattern, '\\'));
        matches.add(builder.like(
                builder.lower(root.get("sellerProfile").get("businessName")), pattern, '\\'));

        Subquery<UUID> withMatchingProduct = query.subquery(UUID.class);
        Root<PurchaseItem> item = withMatchingProduct.from(PurchaseItem.class);
        matches.add(root.get("uuid").in(withMatchingProduct
                .select(item.get("purchase").get("uuid"))
                .where(builder.like(
                        builder.lower(item.join("listing", JoinType.INNER).get("title")),
                        pattern, '\\'))));

        Subquery<String> matchingBuyers = query.subquery(String.class);
        Root<UserProfile> buyer = matchingBuyers.from(UserProfile.class);
        matches.add(root.get("buyerId").in(matchingBuyers
                .select(buyer.get("id"))
                .where(builder.or(
                        builder.like(builder.lower(buyer.get("fullName")), pattern, '\\'),
                        builder.like(builder.lower(buyer.get("email")), pattern, '\\'),
                        builder.like(builder.lower(buyer.get("phone")), pattern, '\\')))));

        return builder.or(matches.toArray(new Predicate[0]));
    }

    /** Escaped, so a search for {@code 100%} looks for that and not for everything. */
    private static String likePattern(String search) {
        String escaped = search.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
