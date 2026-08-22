package co.istad.projectpracticum.phsardigital.features.listings.dto;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One facet of a catalogue search: an attribute, and the values that would satisfy it.
 *
 * <p>Sent as repeated {@code attr=key:value} parameters. Different keys narrow the search
 * together and repeats of one key widen it, which is how a shopper reads a facet panel —
 * ticking both 8 GB and 12 GB asks for either, while also ticking AMOLED asks for both
 * things at once.
 *
 * <pre>{@code ?attr=ram:8 GB&attr=ram:12 GB&attr=panel:AMOLED}</pre>
 * reads as {@code (ram = 8 GB OR ram = 12 GB) AND panel = AMOLED}.
 */
public record AttributeFilter(String key, Set<String> values) {

    /**
     * Parses the raw parameters, collapsing repeats of a key into one facet.
     *
     * @return an empty list when nothing was sent, so a caller need not check for null
     * @throws ResponseStatusException on a parameter with no colon, or an empty half —
     *                                 silently ignoring it would answer a narrower
     *                                 question than the one asked, without saying so
     */
    public static List<AttributeFilter> parse(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        Map<String, Set<String>> byKey = new LinkedHashMap<>();
        for (String parameter : raw) {
            if (parameter == null || parameter.isBlank()) {
                continue;
            }
            // The first colon only: a value may well contain one, as "6.7:1" does.
            int separator = parameter.indexOf(':');
            if (separator < 0) {
                throw malformed(parameter);
            }
            String key = parameter.substring(0, separator).trim();
            String value = parameter.substring(separator + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                throw malformed(parameter);
            }
            byKey.computeIfAbsent(key.toLowerCase(Locale.ROOT), ignored -> new LinkedHashSet<>())
                    .add(value.toLowerCase(Locale.ROOT));
        }

        List<AttributeFilter> filters = new ArrayList<>();
        byKey.forEach((key, values) -> filters.add(new AttributeFilter(key, values)));
        return filters;
    }

    private static ResponseStatusException malformed(String parameter) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Attribute filter '" + parameter + "' must be written key:value, for example attr=ram:8 GB.");
    }
}
