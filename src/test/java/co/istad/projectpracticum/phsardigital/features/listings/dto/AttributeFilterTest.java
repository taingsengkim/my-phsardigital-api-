package co.istad.projectpracticum.phsardigital.features.listings.dto;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How {@code ?attr=key:value} is read off the query string. */
class AttributeFilterTest {

    @Test
    void answersEmptyWhenNoFacetWasAsked() {
        assertThat(AttributeFilter.parse(null)).isEmpty();
        assertThat(AttributeFilter.parse(List.of())).isEmpty();
    }

    /** Repeats of a key widen the search, so they belong to one facet, not two. */
    @Test
    void collapsesRepeatsOfOneKeyIntoASingleFacet() {
        List<AttributeFilter> filters = AttributeFilter.parse(
                List.of("ram:8 GB", "ram:12 GB", "panel:AMOLED"));

        assertThat(filters).hasSize(2);
        assertThat(filters.getFirst().key()).isEqualTo("ram");
        assertThat(filters.getFirst().values()).containsExactly("8 gb", "12 gb");
        assertThat(filters.get(1).values()).containsExactly("amoled");
    }

    /** Stored values are normalised, not lowercased, so the comparison is case-folded. */
    @Test
    void foldsCaseOnBothHalves() {
        AttributeFilter filter = AttributeFilter.parse(List.of("Panel:AMOLED")).getFirst();

        assertThat(filter.key()).isEqualTo("panel");
        assertThat(filter.values()).containsExactly("amoled");
    }

    /** A ratio like 6.7:1 is a legitimate value, so only the first colon splits. */
    @Test
    void splitsOnTheFirstColonOnly() {
        AttributeFilter filter = AttributeFilter.parse(List.of("aspect_ratio:6.7:1")).getFirst();

        assertThat(filter.values()).containsExactly("6.7:1");
    }

    @Test
    void trimsAroundTheSeparator() {
        AttributeFilter filter = AttributeFilter.parse(List.of("  ram : 8 GB  ")).getFirst();

        assertThat(filter.key()).isEqualTo("ram");
        assertThat(filter.values()).containsExactly("8 gb");
    }

    /**
     * Dropping a malformed facet silently would answer a broader question than the one
     * asked without saying so, which is worse than refusing it.
     */
    @Test
    void refusesAFacetThatIsNotKeyColonValue() {
        assertThatThrownBy(() -> AttributeFilter.parse(List.of("ram")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("key:value");
        assertThatThrownBy(() -> AttributeFilter.parse(List.of("ram:")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("key:value");
        assertThatThrownBy(() -> AttributeFilter.parse(List.of(":8 GB")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("key:value");
    }

    /** An empty repeated parameter is what a form submits for an unticked box. */
    @Test
    void ignoresAnEmptyParameter() {
        assertThat(AttributeFilter.parse(Arrays.asList("", null, "ram:8 GB")))
                .singleElement()
                .extracting(AttributeFilter::key)
                .isEqualTo("ram");
    }

    @Test
    void limitsFacetCountValueCountAndParameterLength() {
        List<String> tooManyFacets = IntStream.rangeClosed(1, 13)
                .mapToObj(index -> "key" + index + ":value")
                .toList();
        List<String> tooManyValues = IntStream.rangeClosed(1, 51)
                .mapToObj(index -> "ram:value" + index)
                .toList();

        assertThatThrownBy(() -> AttributeFilter.parse(tooManyFacets))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("12 attribute facets");
        assertThatThrownBy(() -> AttributeFilter.parse(tooManyValues))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("50 values");
        assertThatThrownBy(() -> AttributeFilter.parse(List.of("ram:" + "x".repeat(197))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("200 characters");
    }

    @Test
    void reportsFacetsOnTheListingFilter() {
        ListingFilter withFacet = new ListingFilter(null, null, null, null, null, null,
                AttributeFilter.parse(List.of("ram:8 GB")));
        ListingFilter withoutFacet = new ListingFilter(null, null, null, null, null, null);

        assertThat(withFacet.isEmpty()).isFalse();
        assertThat(withoutFacet.isEmpty()).isTrue();
    }
}
