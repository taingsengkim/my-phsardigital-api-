package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttribute;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeOption;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeResolver;
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
 * Checks what a seller filled in against what the listing's category asks for.
 *
 * <p>Two things it deliberately does <em>not</em> do. It does not reject keys the category
 * has never heard of — a seller who wants to advertise the colour of the box should be
 * able to, and rejecting them would strand every listing written before the category had
 * a schema. And it does not reject a value for being unusual, only for being wrong: the
 * type, the option list and the bounds are the whole of the rule.
 *
 * <p>What it returns is the <em>normalised</em> set. "6.70" becomes "6.7", "amoled" becomes
 * "AMOLED", "yes" becomes "true" — because a facet filter compares stored values, and two
 * sellers typing the same spec differently must end up on the same shelf.
 */
@Component
@RequiredArgsConstructor
public class ListingAttributeValidator {

    /** Both columns on {@code listing_attributes} are {@code varchar(100)}. */
    private static final int MAX_LENGTH = 100;

    private static final Set<String> TRUTHY = Set.of("true", "yes", "y", "1");
    private static final Set<String> FALSY = Set.of("false", "no", "n", "0");

    private final CategoryAttributeResolver categoryAttributeResolver;

    /** One key/value pair as it arrived, before anything has been checked. */
    public record SubmittedAttribute(String key, String value) {
    }

    /**
     * @param definition the category attribute it answers, or null when the seller
     *                   supplied a spec the category does not define
     */
    public record ValidatedAttribute(String key, String value, CategoryAttribute definition) {
    }

    /**
     * @param submitted every attribute the listing will end up with, not just the ones
     *                  being changed — the required check can only be made against the
     *                  final state
     * @return the same attributes, normalised, in the order given
     */
    public List<ValidatedAttribute> validate(Category category, List<SubmittedAttribute> submitted) {
        // Resolved once and passed down: the walk up the category tree is a query, and
        // the required check below needs the same list the lookup is built from.
        List<CategoryAttribute> schema = categoryAttributeResolver.effectiveFor(category);
        Map<String, CategoryAttribute> lookup = CategoryAttributeResolver.lookup(schema);

        List<ValidatedAttribute> validated = new ArrayList<>();
        // Keyed on the canonical form, so "Screen Size" and screen_size are caught as the
        // one duplicate they are rather than saved as two attributes of the same spec.
        Map<String, String> seen = new LinkedHashMap<>();

        for (SubmittedAttribute attribute : submitted == null ? List.<SubmittedAttribute>of() : submitted) {
            String rawKey = attribute.key() == null ? "" : attribute.key().trim();
            if (rawKey.isEmpty()) {
                throw badRequest("Attribute key is required.");
            }

            CategoryAttribute definition = lookup.get(CategoryAttributeResolver.normaliseKey(rawKey));
            String key = definition == null ? rawKey : definition.getCode();

            String canonical = CategoryAttributeResolver.normaliseKey(key);
            String previous = seen.putIfAbsent(canonical, rawKey);
            if (previous != null) {
                throw badRequest(previous.equals(rawKey)
                        ? "Duplicate attribute: " + rawKey
                        : "'" + rawKey + "' and '" + previous + "' are the same attribute.");
            }

            if (key.length() > MAX_LENGTH) {
                throw badRequest("Attribute key must not exceed " + MAX_LENGTH + " characters: " + key);
            }
            validated.add(new ValidatedAttribute(key, normalise(definition, key, attribute.value()), definition));
        }

        requireMandatory(category, schema, seen.keySet());
        return validated;
    }

    /** Every required attribute of the category, and whether the listing answered it. */
    private void requireMandatory(Category category, List<CategoryAttribute> schema, Set<String> present) {
        List<String> missing = schema.stream()
                .filter(attribute -> Boolean.TRUE.equals(attribute.getRequired()))
                .filter(attribute -> !present.contains(CategoryAttributeResolver.normaliseKey(attribute.getCode())))
                .map(attribute -> attribute.getLabel() + " (" + attribute.getCode() + ")")
                .toList();

        if (!missing.isEmpty()) {
            throw badRequest(category.getName() + " listings must specify " + String.join(", ", missing) + ".");
        }
    }

    private String normalise(CategoryAttribute definition, String key, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            throw badRequest("A value is required for attribute '" + key + "'.");
        }
        if (definition == null) {
            return requireFits(key, value);
        }

        // A choice type with no options left is a half-finished admin edit, not a reason
        // to refuse the seller's listing; it falls through to plain text.
        AttributeDataType type = definition.isChoice() && definition.getOptions().isEmpty()
                ? AttributeDataType.TEXT
                : definition.getDataType();

        String normalised = switch (type) {
            case NUMBER -> normaliseNumber(definition, value);
            case BOOLEAN -> normaliseBoolean(definition, value);
            case SELECT -> matchOption(definition, value);
            case MULTI_SELECT -> normaliseMultiSelect(definition, value);
            case TEXT -> value;
        };
        return requireFits(key, normalised);
    }

    private String normaliseNumber(CategoryAttribute definition, String value) {
        BigDecimal number;
        try {
            number = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw badRequest(label(definition) + " must be a number"
                    + (definition.getUnit() == null
                    ? "." : ", in " + definition.getUnit() + " — leave the unit out of the value."));
        }
        double actual = number.doubleValue();
        if (definition.getMinValue() != null && actual < definition.getMinValue()) {
            throw badRequest(label(definition) + " must be at least "
                    + trim(definition.getMinValue()) + unitSuffix(definition) + ".");
        }
        if (definition.getMaxValue() != null && actual > definition.getMaxValue()) {
            throw badRequest(label(definition) + " must be at most "
                    + trim(definition.getMaxValue()) + unitSuffix(definition) + ".");
        }
        // toPlainString rather than toString: stripping the zeros off "100" leaves a
        // BigDecimal that prints as 1E+2.
        return number.stripTrailingZeros().toPlainString();
    }

    private String normaliseBoolean(CategoryAttribute definition, String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (TRUTHY.contains(lower)) {
            return "true";
        }
        if (FALSY.contains(lower)) {
            return "false";
        }
        throw badRequest(label(definition) + " must be true or false.");
    }

    private String normaliseMultiSelect(CategoryAttribute definition, String value) {
        // A set, so listing the same option twice is tidied rather than rejected — it is
        // a typo, not a different meaning.
        Set<String> chosen = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            String token = part.trim();
            if (!token.isEmpty()) {
                chosen.add(matchOption(definition, token));
            }
        }
        if (chosen.isEmpty()) {
            throw badRequest(label(definition) + " needs at least one value.");
        }
        return String.join(", ", chosen);
    }

    /** Matches on value or label, ignoring case, and answers with the canonical value. */
    private String matchOption(CategoryAttribute definition, String value) {
        for (CategoryAttributeOption option : definition.getOptions()) {
            if (option.getValue().equalsIgnoreCase(value)
                    || option.displayLabel().equalsIgnoreCase(value)) {
                return option.getValue();
            }
        }
        String allowed = definition.getOptions().stream()
                .map(CategoryAttributeOption::displayLabel)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        throw badRequest("'" + value + "' is not a valid " + label(definition)
                + ". Choose one of: " + allowed + ".");
    }

    private String requireFits(String key, String value) {
        if (value.length() > MAX_LENGTH) {
            throw badRequest("Value for '" + key + "' must not exceed " + MAX_LENGTH + " characters.");
        }
        return value;
    }

    private static String label(CategoryAttribute definition) {
        return definition.getLabel();
    }

    private static String unitSuffix(CategoryAttribute definition) {
        return definition.getUnit() == null ? "" : " " + definition.getUnit();
    }

    /** 6.0 reads as 6 in a message; 6.5 still reads as 6.5. */
    private static String trim(Double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
