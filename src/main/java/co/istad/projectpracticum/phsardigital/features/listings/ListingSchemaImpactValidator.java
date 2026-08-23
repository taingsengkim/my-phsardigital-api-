package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryAvailability;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttribute;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeResolver;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttributeValidator;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttributeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;

/**
 * Protects already-published listings when an administrator changes the effective
 * attribute schema of a category tree.
 *
 * <p>The caller performs the category/schema mutation first and invokes this guard in
 * the same transaction. Every public listing is then re-applied through the normal
 * writer, so inherited-definition links and canonical values are refreshed as well as
 * validated. Any invalid listing rejects the administrative mutation atomically.
 */
@Component
@RequiredArgsConstructor
public class ListingSchemaImpactValidator {

    private final ListingRepository listingRepository;
    private final ListingAttributeWriter listingAttributeWriter;
    private final CategoryAvailability categoryAvailability;
    private final CategoryAttributeResolver categoryAttributeResolver;

    public void revalidatePublishedListings(Category subtreeRoot) {
        List<Category> categories = categoriesIn(subtreeRoot);
        for (Category category : categories) {
            long requiredAttributeCount = categoryAttributeResolver.effectiveFor(category).stream()
                    .map(CategoryAttribute::getRequired)
                    .filter(Boolean.TRUE::equals)
                    .count();
            if (requiredAttributeCount > ListingAttributeValidator.MAX_ATTRIBUTES) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Category '" + category.getSlug() + "' would have "
                                + requiredAttributeCount + " required effective attributes, but a listing "
                                + "can submit at most " + ListingAttributeValidator.MAX_ATTRIBUTES + ".");
            }
        }

        Set<UUID> categoryUuids = categories.stream()
                .map(Category::getUuid)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (categoryUuids.isEmpty()) {
            return;
        }

        for (Listing listing : listingRepository.findAllByCategory_UuidIn(categoryUuids)) {
            if (!isPublished(listing.getStatus())
                    || !categoryAvailability.isEffectivelyActive(listing.getCategory())) {
                continue;
            }
            try {
                listingAttributeWriter.apply(listing, listingAttributeWriter.currentOf(listing));
            } catch (ResponseStatusException exception) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The category schema change would invalidate published listing "
                                + listing.getUuid() + ". Update or archive that listing first. "
                                + exception.getReason(),
                        exception);
            }
        }
    }

    private static boolean isPublished(ListingStatus status) {
        return status == ListingStatus.ACTIVE || status == ListingStatus.SOLD_OUT;
    }

    private static List<Category> categoriesIn(Category root) {
        List<Category> categories = new java.util.ArrayList<>();
        Set<Category> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Category> pending = new ArrayDeque<>();
        if (root != null) {
            pending.add(root);
        }

        while (!pending.isEmpty()) {
            Category category = pending.removeFirst();
            if (!visited.add(category)) {
                continue;
            }
            categories.add(category);
            if (category.getChildCategories() != null) {
                category.getChildCategories().stream()
                        .filter(java.util.Objects::nonNull)
                        .forEach(pending::addLast);
            }
        }
        return List.copyOf(categories);
    }
}
