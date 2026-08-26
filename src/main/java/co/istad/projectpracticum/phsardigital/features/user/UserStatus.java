package co.istad.projectpracticum.phsardigital.features.user;

/**
 * An account's standing.
 *
 * <p>{@link #SUSPENDED} and {@link #BANNED} are both admin moderation outcomes and are
 * kept apart deliberately: a suspension is a temporary hold an admin expects to lift,
 * a ban is permanent. Collapsing them would make the two indistinguishable on the
 * admin's own screen and in any later appeal.
 *
 * <p>Persisted by name, so adding a constant needs no migration for existing rows.
 */
public enum UserStatus {

    ACTIVE, PENDING, SUSPENDED, REJECTED,

    /** Permanently removed from the marketplace by an admin. */
    BANNED
}
