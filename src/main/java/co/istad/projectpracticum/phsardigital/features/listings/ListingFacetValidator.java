package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttribute;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeOption;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeResolver;
import co.istad.projectpracticum.phsardigital.features.listings.dto.AttributeFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves public facet parameters against the category schema before they reach SQL.
 * This makes {@code filterable} an enforced rule rather than documentation and converts
 * labels and alternate number/boolean spellings to the same values listing writes use.
 */
@Component
@RequiredArgsConstructor
class ListingFacetValidator {

    private static final Set<String> TRUTHY = Set.of("true", "yes", "y", "1");
    private static final Set<String> FALSY = Set.of("false", "no", "n", "0");

    private final CategoryAttributeResolver categoryAttributeResolver;

    List<AttributeFilter> validate(Category category, List<AttributeFilter> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }

        Map<String, CategoryAttribute> byCode = new LinkedHashMap<>();
        for (CategoryAttribute definition : categoryAttributeResolver.effectiveFor(category)) {
            byCode.put(CategoryAttributeResolver.normaliseKey(definition.getCode()), definition);
        }

        List<AttributeFilter> validated = new ArrayList<>();
        for (AttributeFilter filter : requested) {
            if (filter == null || filter.values() == null || filter.values().isEmpty()) {
                throw badRequest("Every attribute filter needs at least one value.");
            }
            String code = CategoryAttributeResolver.normaliseKey(filter.key());
            CategoryAttribute definition = byCode.get(code);
            if (definition == null) {
                throw badRequest("Unknown attribute filter '" + filter.key()
                        + "' for category '" + category.getSlug() + "'.");
            }
            if (!Boolean.TRUE.equals(definition.getFilterable())) {
                throw badRequest("Attribute '" + definition.getCode() + "' is not filterable.");
            }

            Set<String> values = new LinkedHashSet<>();
            for (String value : filter.values()) {
                values.add(normalise(definition, value));
            }
            validated.add(new AttributeFilter(definition.getCode(), values));
        }
        return List.copyOf(validated);
    }

    private String normalise(CategoryAttribute definition, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            throw badRequest("A value is required for attribute filter '"
                    + definition.getCode() + "'.");
        }

        AttributeDataType type = definition.getDataType();
        return switch (type) {
            case NUMBER -> number(definition, value);
            case BOOLEAN -> bool(definition, value);
            case SELECT, MULTI_SELECT -> option(definition, value);
            case TEXT -> value;
        };
    }

    private String number(CategoryAttribute definition, String value) {
        BigDecimal number;
        try {
            number = new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw badRequest("Filter for '" + definition.getCode() + "' must be a number.");
        }

        if (definition.getMinValue() != null
                && number.compareTo(BigDecimal.valueOf(definition.getMinValue())) < 0) {
            throw badRequest("Filter for '" + definition.getCode() + "' is below its minimum.");
        }
        if (definition.getMaxValue() != null
                && number.compareTo(BigDecimal.valueOf(definition.getMaxValue())) > 0) {
            throw badRequest("Filter for '" + definition.getCode() + "' is above its maximum.");
        }
        return number.stripTrailingZeros().toPlainString();
    }

    private String bool(CategoryAttribute definition, String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (TRUTHY.contains(lower)) {
            return "true";
        }
        if (FALSY.contains(lower)) {
            return "false";
        }
        throw badRequest("Filter for '" + definition.getCode() + "' must be true or false.");
    }

    private String option(CategoryAttribute definition, String value) {
        for (CategoryAttributeOption option : definition.getOptions()) {
            if (option.getValue().equalsIgnoreCase(value)
                    || option.displayLabel().equalsIgnoreCase(value)) {
                return option.getValue();
            }
        }
        throw badRequest("'" + value + "' is not an option for attribute '"
                + definition.getCode() + "'.");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
