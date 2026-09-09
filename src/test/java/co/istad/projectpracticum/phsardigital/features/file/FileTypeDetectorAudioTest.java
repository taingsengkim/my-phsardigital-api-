package co.istad.projectpracticum.phsardigital.features.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Voice notes are identified the same way everything else here is: by their leading
 * bytes. These cases are built from the real container headers rather than from files,
 * so they document what each format actually looks like.
 */
class FileTypeDetectorAudioTest {

    @Test
    @DisplayName("reads WebM, which is what Chrome and Firefox record into")
    void detectsWebm() {
        // EBML magic, then the DocType element naming "webm" a little way in.
        byte[] header = concat(
                bytes(0x1A, 0x45, 0xDF, 0xA3, 0x9F, 0x42, 0x86, 0x81, 0x01, 0x42, 0x82, 0x84),
                "webm".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(detect(header)).isEqualTo(FileTypeDetector.AUDIO_WEBM);
    }

    @Test
    @DisplayName("does not mistake a Matroska video for a voice note")
    void rejectsMatroska() {
        // The same EBML magic, but the DocType says "matroska".
        byte[] header = concat(
                bytes(0x1A, 0x45, 0xDF, 0xA3, 0x9F, 0x42, 0x86, 0x81, 0x01, 0x42, 0x82, 0x88),
                "matroska".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(detect(header)).isNull();
    }

    @Test
    @DisplayName("reads Ogg, which carries Opus")
    void detectsOgg() {
        assertThat(detect(concat("OggS".getBytes(StandardCharsets.ISO_8859_1),
                bytes(0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))))
                .isEqualTo(FileTypeDetector.AUDIO_OGG);
    }

    @Test
    @DisplayName("reads the ISO brands Safari writes, since it cannot record WebM")
    void detectsIsoMediaAudio() {
        for (String brand : new String[]{"M4A ", "mp42", "isom", "iso2", "M4B ", "mp41"}) {
            assertThat(detect(isoMedia(brand)))
                    .as("brand %s", brand)
                    .isEqualTo(FileTypeDetector.AUDIO_MP4);
        }
    }

    @Test
    @DisplayName("rejects an ISO container whose brand is not one a recorder writes")
    void rejectsUnknownIsoBrand() {
        assertThat(detect(isoMedia("qt  "))).isNull();
    }

    @Test
    @DisplayName("reads MP3, both tagged and raw")
    void detectsMpegAudio() {
        assertThat(detect(concat("ID3".getBytes(StandardCharsets.ISO_8859_1),
                bytes(0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))))
                .isEqualTo(FileTypeDetector.AUDIO_MPEG);
        // A bare frame: eleven bits of sync word set.
        assertThat(detect(bytes(0xFF, 0xFB, 0x90, 0x64, 0x00, 0x00, 0x00, 0x00)))
                .isEqualTo(FileTypeDetector.AUDIO_MPEG);
    }

    @Test
    @DisplayName("still identifies the formats that existed before audio did")
    void doesNotDisturbTheExistingFormats() {
        assertThat(detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo(FileTypeDetector.IMAGE_JPEG);
        assertThat(detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
                .isEqualTo(FileTypeDetector.IMAGE_PNG);
        assertThat(detect(concat("RIFF".getBytes(StandardCharsets.ISO_8859_1),
                concat(bytes(0x00, 0x00, 0x00, 0x00), "WEBP".getBytes(StandardCharsets.ISO_8859_1)))))
                .isEqualTo(FileTypeDetector.IMAGE_WEBP);
        assertThat(detect("%PDF-1.7".getBytes(StandardCharsets.ISO_8859_1)))
                .isEqualTo(FileTypeDetector.APPLICATION_PDF);
    }

    @Test
    @DisplayName("a WAV file is a RIFF container too, and is not on the allowlist")
    void rejectsWav() {
        // RIFF....WAVE — the same first four bytes as WEBP, so this is the case that
        // proves the container check reads past them.
        byte[] header = concat("RIFF".getBytes(StandardCharsets.ISO_8859_1),
                concat(bytes(0x24, 0x08, 0x00, 0x00), "WAVE".getBytes(StandardCharsets.ISO_8859_1)));

        assertThat(detect(header)).isNull();
    }

    @Test
    @DisplayName("an executable renamed to .webm is still refused")
    void rejectsDisguisedExecutable() {
        byte[] header = concat(bytes(0x4D, 0x5A, 0x90, 0x00),   // "MZ" — a Windows PE
                "webm".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(detect(header)).isNull();
    }

    @Test
    @DisplayName("a file too short to carry any signature is refused rather than throwing")
    void rejectsTruncatedFile() {
        assertThat(detect(bytes(0x1A, 0x45))).isNull();
        assertThat(detect(new byte[0])).isNull();
    }

    private static String detect(byte[] content) {
        return FileTypeDetector.detect(
                new MockMultipartFile("file", "recording", "audio/webm", content));
    }

    /** Four bytes of box size, "ftyp", then the brand. */
    private static byte[] isoMedia(String brand) {
        return concat(concat(bytes(0x00, 0x00, 0x00, 0x20),
                        "ftyp".getBytes(StandardCharsets.ISO_8859_1)),
                brand.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(left);
        out.writeBytes(right);
        return out.toByteArray();
    }
}
