package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile extends BasedEntity {

    public UserProfile(String userId) {
        this.id = userId;
    }

    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(unique = true, length = 100)
    private String username;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /**
     * Denormalized {@code firstName + lastName}, kept for list screens and search.
     */
    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(length = 30)
    private String phone;

    /**
     * Profile picture stored in MinIO. Null means the client should fall back to
     * initials or a placeholder.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "avatar_file_id")
    private FileUpload avatarFile;

    /**
     * Profile picture hosted by the identity provider — the {@code picture} claim
     * Google and Facebook supply. Kept as a URL rather than copied into MinIO: it is
     * somebody else's image on somebody else's CDN, and re-hosting it on every login
     * would be a download per sign-in for a picture the user may replace anyway.
     *
     * <p>Used only as a fallback. {@link #avatarFile} wins whenever it is set, so an
     * avatar the user deliberately uploaded is never overwritten by their Google
     * photo on the next login.
     */
    @Column(name = "external_avatar_url", length = 1000)
    private String externalAvatarUrl;

    /**
     * Which identity provider the account came through — {@code google},
     * {@code facebook}, or null for an account registered with a password here.
     *
     * <p>Populated from the {@code identity_provider} claim, which Keycloak only
     * emits once a hardcoded claim mapper is added to the client; without that
     * mapper this stays null and nothing else changes.
     */
    @Column(name = "identity_provider", length = 50)
    private String identityProvider;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(length = 500)
    private String bio;

    /**
     * Mirror of the Keycloak flag, refreshed whenever the profile is read or
     * updated, so callers do not need a second round trip to the identity server.
     */
    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    // Who last changed the standing above and why. Kept on the profile rather than
    // inferred from the status, because a restored account keeps no trace otherwise —
    // and "why was this account banned last year" is exactly what an admin asks.

    @Column(name = "moderated_by")
    private String moderatedBy;

    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    @Column(name = "moderation_reason", columnDefinition = "TEXT")
    private String moderationReason;

    /**
     * Recomputes {@link #fullName} from the current name parts.
     */
    public void refreshFullName() {
        String first = firstName == null ? "" : firstName;
        String last = lastName == null ? "" : lastName;
        String combined = (first + " " + last).trim();
        this.fullName = combined.isBlank() ? null : combined;
    }
}
