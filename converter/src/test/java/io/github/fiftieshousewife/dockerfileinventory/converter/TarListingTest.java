package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.jupiter.api.Test;

class TarListingTest {

    private static final String SHA_OF_X = "2d711642b726b04401627ca9fbac32f5c8530fb1903cc4db02258717921a4881";

    @Test
    void eachEntryHasItsTypeModeOwnerTargetAndTheSumOfItsContentAHardLinkThatOfItsTarget() throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        try (TarArchiveOutputStream out = new TarArchiveOutputStream(tar)) {
            out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            add(out, directory("./", 0755, 0));
            add(out, file("./opt/app.jar", 0644, 1001), "x");
            add(out, link("./bin", "usr/bin", TarConstants.LF_SYMLINK));
            add(out, link("./opt/copy.jar", "./opt/app.jar", TarConstants.LF_LINK));
        }

        assertThat(TarListing.of(new ByteArrayInputStream(tar.toByteArray()))).containsExactly(
                Map.entry("/", new FileEntry("d", "755", "0", "0", "", null)),
                Map.entry("/bin", new FileEntry("l", "777", "0", "0", "usr/bin", null)),
                Map.entry("/opt/app.jar", new FileEntry("f", "644", "1001", "0", "", SHA_OF_X)),
                Map.entry("/opt/copy.jar", new FileEntry("f", "644", "0", "0", "", SHA_OF_X)));
    }

    @Test
    void aTarNameBecomesAnAbsolutePathWithoutATrailingSlash() {
        assertAll(
                () -> assertThat(TarListing.path("./")).isEqualTo("/"),
                () -> assertThat(TarListing.path("./etc/")).isEqualTo("/etc"),
                () -> assertThat(TarListing.path("usr/bin/env")).isEqualTo("/usr/bin/env"));
    }

    @Test
    void aDeviceOrPipeHasFindsLetter() {
        assertAll(
                () -> assertThat(TarListing.type(new TarArchiveEntry("./dev/null", TarConstants.LF_CHR)))
                        .isEqualTo("c"),
                () -> assertThat(TarListing.type(new TarArchiveEntry("./dev/sda", TarConstants.LF_BLK)))
                        .isEqualTo("b"),
                () -> assertThat(TarListing.type(new TarArchiveEntry("./run/p", TarConstants.LF_FIFO)))
                        .isEqualTo("p"));
    }

    private static TarArchiveEntry directory(String name, int mode, int uid) {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setMode(TarArchiveEntry.DEFAULT_DIR_MODE & ~07777 | mode);
        entry.setUserId(uid);
        return entry;
    }

    private static TarArchiveEntry file(String name, int mode, int uid) {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setMode(TarArchiveEntry.DEFAULT_FILE_MODE & ~07777 | mode);
        entry.setUserId(uid);
        entry.setSize(1);
        return entry;
    }

    private static TarArchiveEntry link(String name, String target, byte type) {
        TarArchiveEntry entry = new TarArchiveEntry(name, type);
        entry.setLinkName(target);
        entry.setMode(type == TarConstants.LF_SYMLINK ? 0120777 : 0100644);
        return entry;
    }

    private static void add(TarArchiveOutputStream out, TarArchiveEntry entry) throws IOException {
        out.putArchiveEntry(entry);
        out.closeArchiveEntry();
    }

    private static void add(TarArchiveOutputStream out, TarArchiveEntry entry, String content) throws IOException {
        out.putArchiveEntry(entry);
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeArchiveEntry();
    }
}
