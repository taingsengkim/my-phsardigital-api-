package co.istad.projectpracticum.phsardigital.features.auth.dto;

import co.istad.projectpracticum.phsardigital.features.user.Gender;
import co.istad.projectpracticum.phsardigital.features.user.dto.UpdateUserProfileRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsValidRegistration() {
        RegisterRequest request = new RegisterRequest(
                "sokha.user",
                "correct horse battery staple",
                "correct horse battery staple",
                "sokha@example.com",
                "Sokha",
                "Chan",
                "012345678"
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsMalformedRegistrationFields() {
        RegisterRequest request = new RegisterRequest(
                "bad username",
                "short",
                "short",
                "not-an-email",
                "",
                "",
                "+855123"
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("username", "password", "email", "firstName", "lastName", "phoneNumber");
    }

    @Test
    void rejectsBlankProfileNamesAndFutureBirthDate() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "   ",
                "   ",
                "abc",
                LocalDate.now().plusDays(1),
                Gender.FEMALE,
                "Sells handmade baskets in Phnom Penh."
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("firstName", "lastName", "phone", "dateOfBirth");
    }

    @Test
    void acceptsProfileUpdateWithOptionalFieldsOmitted() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "Sokha",
                "Chan",
                "012345678",
                LocalDate.of(2000, 1, 1),
                null,
                null
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsOverlongBio() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                null,
                null,
                null,
                null,
                Gender.PREFER_NOT_TO_SAY,
                "a".repeat(501)
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("bio");
    }
}
