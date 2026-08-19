package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The shop name is optional on a PATCH but not erasable. */
class SellerProfileUpdateRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static SellerProfileUpdateRequest withBusinessName(String businessName) {
        return new SellerProfileUpdateRequest(
                businessName, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    @Test
    void omittingTheBusinessNameLeavesItAlone() {
        assertThat(validator.validate(withBusinessName(null))).isEmpty();
    }

    @Test
    void rejectsAnEmptyBusinessName() {
        assertThat(validator.validate(withBusinessName("")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("businessName");
    }

    @Test
    void rejectsAWhitespaceOnlyBusinessName() {
        assertThat(validator.validate(withBusinessName("   ")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("businessName");
    }

    @Test
    void acceptsARealBusinessName() {
        assertThat(validator.validate(withBusinessName("Phsar Thmey Handicrafts"))).isEmpty();
    }

    @Test
    void acceptsAMultiLineBusinessName() {
        assertThat(validator.validate(withBusinessName("Phsar Thmey\nHandicrafts"))).isEmpty();
    }

    @Test
    void stillRejectsAnOverlongBusinessName() {
        assertThat(validator.validate(withBusinessName("a".repeat(256))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("businessName");
    }
}
