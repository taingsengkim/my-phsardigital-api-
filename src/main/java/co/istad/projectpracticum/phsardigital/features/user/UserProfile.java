package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

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
