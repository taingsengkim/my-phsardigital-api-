package co.istad.projectpracticum.phsardigital.features.file;

/**
 * Decides which bucket an object is written to and how its URL is handed out.
 *
 * <p>{@link #PUBLIC} objects live in the anonymous-read bucket and are addressed
 * by a plain, permanent URL — that is what makes {@code <img src>} work without a
 * token. {@link #PRIVATE} objects live in a bucket with no anonymous policy and
 * are only reachable through a short-lived presigned URL, so identity documents
 * are not readable by anybody who happens to learn the object name.
 */
public enum FileVisibility {
    PUBLIC,
    PRIVATE
}
