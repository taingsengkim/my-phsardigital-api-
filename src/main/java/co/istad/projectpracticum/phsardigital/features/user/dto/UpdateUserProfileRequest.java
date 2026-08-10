package co.istad.projectpracticum.phsardigital.features.user.dto;

import co.istad.projectpracticum.phsardigital.features.user.Gender;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Partial profile update: every field is optional and only non-null values are
 * applied. The avatar is not part of this payload — it is managed through the
 * dedicated {@code /me/avatar} upload endpoint.
 */
public record UpdateUserProfileRequest(
        @Size(max = 100, message = "First name must not exceed 100 characters")
        @Pattern(regexp = ".*\\S.*", message = "First name must not be blank")
        String firstName,

        @Size(max = 100, message = "Last name must not exceed 100 characters")
        @Pattern(regexp = ".*\\S.*", message = "Last name must not be blank")
        String lastName,

        @Pattern(regexp = "^\\d{9,11}$", message = "Phone number must contain 9 to 11 digits")
        String phone,

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        Gender gender,

        @Size(max = 500, message = "Bio must not exceed 500 characters")
        String bio
) {
}
