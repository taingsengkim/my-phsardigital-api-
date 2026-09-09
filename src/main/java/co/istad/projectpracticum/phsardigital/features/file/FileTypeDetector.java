package co.istad.projectpracticum.phsardigital.features.file;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/**
 * Identifies an upload by its leading bytes rather than by what the client said
 * it was.
 *
 * <p>The {@code Content-Type} on a multipart part and the extension on the
 * filename are both written by the caller, so neither can gate an allowlist: a
 * script renamed to {@code avatar.png} and sent as {@code image/png} passes every
 * check that trusts them. Only the bytes are evidence. The type returned here is
 * what gets stored on the object and in the database, so a caller can never make
 * storage serve their file back under a type it does not have.
 *
 * <p>Deliberately covers only the handful of formats the API accepts. An
 * unrecognised file returns {@code null} and is rejected by the caller — an
 * allowlist that fails closed beats a general-purpose sniffer that guesses.
 */
public final class FileTypeDetector {

    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_PNG = "image/png";
    public static final String IMAGE_GIF = "image/gif";
    public static final String IMAGE_WEBP = "image/webp";
    public static final String APPLICATION_PDF = "application/pdf";

    /**
     * Modern Office formats are ZIP containers, so their signature is ZIP's. That
     * makes this the one coarse entry here: any ZIP renamed {@code .docx} matches.
     * Accepted anyway because a business licence often arrives as a Word file, and
     * the blast radius is small — documents go to the private bucket, are served
     * only through an expiring presigned URL, and carry
     * {@code Content-Disposition: attachment}, so storage never renders one.
     */
    public static final String APPLICATION_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** Legacy Word, an OLE2 compound file. Same caveat as {@link #APPLICATION_DOCX}. */
    public static final String APPLICATION_MSWORD = "application/msword";

    /**
     * What a browser's {@code MediaRecorder} produces for a voice note: WebM/Opus in
     * Chrome and Firefox, MP4/AAC in Safari. OGG and MPEG are here for clients that
     * transcode before uploading.
     *
     * <p>These four are container formats, and a container does not say whether it holds
     * a video track. So this is coarse in the same way {@link #APPLICATION_DOCX} is: an
     * MP4 holding video is accepted as {@code audio/mp4}. Tolerable because a voice note
     * goes to the private bucket, is reachable only through an expiring presigned URL,
     * and is rendered into an {@code <audio>} element — the worst case is somebody
     * sending a video whose soundtrack plays. Telling the two apart needs the track table
     * parsed, not the first sixty-four bytes.
     */
    public static final String AUDIO_WEBM = "audio/webm";

    public static final String AUDIO_OGG = "audio/ogg";

    public static final String AUDIO_MP4 = "audio/mp4";

    public static final String AUDIO_MPEG = "audio/mpeg";

    /**
     * The MP4 brands a voice recorder actually emits. Safari does not write {@code M4A }
     * for {@code MediaRecorder} output — it writes a generic ISO brand — so refusing
     * those would break voice notes on every iPhone.
     */
    private static final Set<String> AUDIO_MP4_BRANDS =
            Set.of("M4A ", "M4B ", "mp41", "mp42", "isom", "iso2");

    /**
     * Enough for every signature below. WEBP needs the first 12; WebM needs room to find
     * the {@code DocType} that separates it from a Matroska file, which sits a little way
     * past the EBML header.
     */
    private static final int HEADER_BYTES = 64;

    private FileTypeDetector() {
    }

    /**
     * @param file the upload to inspect; its stream is opened and closed here and
     *             is untouched afterwards, so the caller can still read it in full
     * @return the detected MIME type, or {@code null} when the bytes match none of
     *         the supported formats
     */
    public static String detect(MultipartFile file) {
        byte[] header = readHeader(file);

        if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
            return IMAGE_JPEG;
        }
        if (startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return IMAGE_PNG;
        }
        // GIF87a and GIF89a share the first five bytes.
        if (startsWith(header, 0x47, 0x49, 0x46, 0x38)
                && header.length > 5
                && ((header[4] & 0xFF) == 0x37 || (header[4] & 0xFF) == 0x39)
                && (header[5] & 0xFF) == 0x61) {
            return IMAGE_GIF;
        }
        if (isWebp(header)) {
            return IMAGE_WEBP;
        }
        if (startsWith(header, 0x25, 0x50, 0x44, 0x46, 0x2D)) { // "%PDF-"
            return APPLICATION_PDF;
        }
        // "PK\x03\x04" — a ZIP local file header, which is what a .docx is.
        if (startsWith(header, 0x50, 0x4B, 0x03, 0x04)) {
            return APPLICATION_DOCX;
        }
        // OLE2 compound file: legacy .doc, and also .xls/.ppt of the same vintage.
        if (startsWith(header, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return APPLICATION_MSWORD;
        }
        // "OggS" — an Ogg page header, which is what Opus and Vorbis are carried in.
        if (startsWith(header, 0x4F, 0x67, 0x67, 0x53)) {
            return AUDIO_OGG;
        }
        if (isWebmContainer(header)) {
            return AUDIO_WEBM;
        }
        if (isIsoMediaAudio(header)) {
            return AUDIO_MP4;
        }
        if (isMpegAudio(header)) {
            return AUDIO_MPEG;
        }
        return null;
    }

    /**
     * An EBML document whose {@code DocType} is "webm".
     *
     * <p>The four-byte EBML magic alone is not enough: Matroska video files start with
     * exactly the same bytes. The {@code DocType} element sits at no fixed offset, so it
     * is searched for within the header rather than read at a known position — which is
     * why {@link #HEADER_BYTES} is larger than any single signature needs.
     */
    private static boolean isWebmContainer(byte[] header) {
        return startsWith(header, 0x1A, 0x45, 0xDF, 0xA3) && contains(header, "webm");
    }

    /**
     * An ISO base media file — MP4 and M4A — whose brand is one a recorder writes.
     *
     * <p>The signature is not at the start: the first four bytes are the size of the
     * {@code ftyp} box, so "ftyp" itself begins at offset 4 and the brand at offset 8.
     */
    private static boolean isIsoMediaAudio(byte[] header) {
        if (header.length < 12) {
            return false;
        }
        boolean hasFtyp = (header[4] & 0xFF) == 0x66 && (header[5] & 0xFF) == 0x74
                && (header[6] & 0xFF) == 0x79 && (header[7] & 0xFF) == 0x70;
        return hasFtyp && AUDIO_MP4_BRANDS.contains(
                new String(header, 8, 4, StandardCharsets.ISO_8859_1));
    }

    /**
     * MPEG audio: either an ID3 tag, or a raw frame whose eleven-bit sync word is set.
     *
     * <p>Deliberately the last check. A frame sync is only eleven bits of evidence, so
     * testing it before the others would let a file that is really something else match
     * on a coincidence.
     */
    private static boolean isMpegAudio(byte[] header) {
        if (startsWith(header, 0x49, 0x44, 0x33)) {      // "ID3"
            return true;
        }
        return header.length >= 2
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xE0) == 0xE0;
    }

    /** Whether the ASCII {@code needle} appears anywhere in the bytes read so far. */
    private static boolean contains(byte[] header, String needle) {
        byte[] target = needle.getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int start = 0; start + target.length <= header.length; start++) {
            for (int offset = 0; offset < target.length; offset++) {
                if (header[start + offset] != target[offset]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * WEBP is a RIFF container: "RIFF", a four-byte length, then "WEBP".
     */
    private static boolean isWebp(byte[] header) {
        return header.length >= 12
                && startsWith(header, 0x52, 0x49, 0x46, 0x46)
                && (header[8] & 0xFF) == 0x57
                && (header[9] & 0xFF) == 0x45
                && (header[10] & 0xFF) == 0x42
                && (header[11] & 0xFF) == 0x50;
    }

    private static boolean startsWith(byte[] header, int... signature) {
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((header[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] readHeader(MultipartFile file) {
        byte[] buffer = new byte[HEADER_BYTES];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(buffer, 0, HEADER_BYTES);
            return read == HEADER_BYTES ? buffer : Arrays.copyOf(buffer, Math.max(read, 0));
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Could not read the uploaded file.", exception);
        }
    }
}
