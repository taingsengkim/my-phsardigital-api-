package co.istad.projectpracticum.phsardigital.features.review.dto;

/**
 * The reviewer, as shown beside their review.
 *
 * <p>Deliberately not {@code UserProfile} or {@code UserProfileResponse}. Reviews are
 * readable without a token, and both of those carry the reviewer's email, phone
 * number and date of birth — answering a shop page with them would publish the
 * contact details of everyone who ever left a review. A name and a picture is the
 * whole of what a review card needs.
 *
 * @param displayName the reviewer's full name, falling back to their username; null
 *                    when they have set neither, which the client renders as
 *                    initials or "Anonymous" rather than exposing the email
 */
public record ReviewAuthorResponse(
        String id,
        String displayName,
        String avatarUrl
) {
}
