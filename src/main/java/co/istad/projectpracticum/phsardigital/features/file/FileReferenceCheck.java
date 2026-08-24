package co.istad.projectpracticum.phsardigital.features.file;

/**
 * Whether some table still points at a stored file. {@code files} is shared, so every
 * path that deletes one consults these first. Each feature contributes its own, which
 * keeps the file package from reaching into every table.
 */
public interface FileReferenceCheck {

    /** What holds the reference, for the caller's message and the log. */
    String referenceName();

    boolean isReferenced(String objectName);

    /** True when the column is {@code NOT NULL}, so the delete must be refused rather than detached. */
    default boolean isMandatory() {
        return false;
    }
}
