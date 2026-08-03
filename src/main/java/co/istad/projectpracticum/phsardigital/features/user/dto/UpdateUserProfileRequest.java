package co.istad.projectpracticum.phsardigital.features.user.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateUserProfileRequest(
        @Size(max = 100, message = "First name must not exceed 100 characters")
        @Pattern(regexp = ".*\\S.*", message = "First name must not be blank")
        String firstName,

        @Size(max = 100, message = "Last name must not exceed 100 characters")
        @Pattern(regexp = ".*\\S.*", message = "Last name must not be blank")
        String lastName,

        @Pattern(regexp = "^\\d{9,11}$", message = "Phone number must contain 9 to 11 digits")
        String phone,

        @Size(max = 2048, message = "Avatar URL must not exceed 2048 characters")
        String avatarUrl,

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth
) {
}
