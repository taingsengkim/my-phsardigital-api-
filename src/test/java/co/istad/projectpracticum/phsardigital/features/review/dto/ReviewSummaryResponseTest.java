package co.istad.projectpracticum.phsardigital.features.review.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewSummaryResponseTest {

    @Test
    void theBreakdownAlwaysHasFiveBarsHighestFirst() {
        // A star nobody has given returns no row from the database, but the bar still
        // has to be drawn — a breakdown missing its 2-star row reads as a rendering bug.
        var summary = ReviewSummaryResponse.of(Map.of(5, 110L, 4, 12L));

        assertThat(summary.breakdown()).hasSize(5);
        assertThat(summary.breakdown()).extracting(ReviewSummaryResponse.StarCount::stars)
                .containsExactly(5, 4, 3, 2, 1);
        assertThat(summary.breakdown().get(2).count()).isZero();
    }

    @Test
    void theAverageIsDerivedFromTheSameCountsTheBarsAreDrawnFrom() {
        // 110*5 + 12*4 + 4*3 + 1*2 + 1*1 = 613 over 128 reviews = 4.789…
        var summary = ReviewSummaryResponse.of(
                Map.of(5, 110L, 4, 12L, 3, 4L, 2, 1L, 1, 1L));

        assertThat(summary.reviewCount()).isEqualTo(128);
        assertThat(summary.averageRating()).isEqualTo(4.8);
    }

    @Test
    void anUnreviewedShopHasNoRatingRatherThanZeroStars() {
        // 0.0 would render as one empty star row and libel a shop that simply has not
        // been reviewed yet.
        var summary = ReviewSummaryResponse.of(Map.of());

        assertThat(summary.averageRating()).isNull();
        assertThat(summary.reviewCount()).isZero();
        assertThat(summary.breakdown()).hasSize(5);
        assertThat(summary.breakdown())
                .allSatisfy(bar -> assertThat(bar.count()).isZero());
    }

    @Test
    void percentagesAreZeroRatherThanADivisionByNothing() {
        var summary = ReviewSummaryResponse.of(Map.of());

        assertThat(summary.breakdown())
                .allSatisfy(bar -> assertThat(bar.percentage()).isEqualByComparingTo("0.0"));
    }

    @Test
    void eachBarCarriesItsShareOfTheTotal() {
        var summary = ReviewSummaryResponse.of(Map.of(5, 3L, 1, 1L));

        assertThat(summary.breakdown().getFirst().percentage()).isEqualByComparingTo("75.0");
        assertThat(summary.breakdown().getLast().percentage()).isEqualByComparingTo("25.0");
    }
}
