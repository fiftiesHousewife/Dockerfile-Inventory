package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class FileDifferencesTest {

    @Test
    void aFileInOneImageOnlyAChangedModeAndChangedContentAreDifferences() {
        SortedMap<String, FileEntry> original = new TreeMap<>();
        original.put("/a", file("644", "s1"));
        original.put("/b", file("644", "s2"));
        original.put("/gone", file("644", "s3"));
        SortedMap<String, FileEntry> regenerated = new TreeMap<>();
        regenerated.put("/a", file("755", "s1"));
        regenerated.put("/b", file("644", "other"));
        regenerated.put("/new", file("644", "s4"));

        assertThat(FileDifferences.of(original, regenerated)).containsExactly("/a: mode 644 became 755",
                "/b: content differs", "/gone: only in the original", "/new: only in the regenerated");
    }

    @Test
    void aVolatilePathsContentIsNotComparedButItsModeIs() {
        SortedMap<String, FileEntry> original = new TreeMap<>();
        original.put("/var/lib/rpm/rpmdb.sqlite", file("644", "s1"));
        original.put("/usr/lib/x.pyc", file("644", "s2"));
        SortedMap<String, FileEntry> regenerated = new TreeMap<>();
        regenerated.put("/var/lib/rpm/rpmdb.sqlite", file("600", "other"));
        regenerated.put("/usr/lib/x.pyc", file("644", "other"));

        assertThat(FileDifferences.of(original, regenerated))
                .containsExactly("/var/lib/rpm/rpmdb.sqlite: mode 644 became 600");
    }

    @Test
    void volatileCoversThePackageDatabasesLogsCachesBytecodeAndTmp() {
        assertAll(
                () -> assertThat(FileDifferences.volatileContent("/var/lib/dpkg/status")).isTrue(),
                () -> assertThat(FileDifferences.volatileContent("/etc/ld.so.cache")).isTrue(),
                () -> assertThat(FileDifferences.volatileContent("/tmp/x")).isTrue(),
                () -> assertThat(FileDifferences.volatileContent("/etc/machine-id")).isTrue(),
                () -> assertThat(FileDifferences.volatileContent("/etc/passwd")).isFalse(),
                () -> assertThat(FileDifferences.volatileContent("/var/lib/rpmx")).isFalse());
    }

    @Test
    void anEntrySaysEachAttributeThatChanged() {
        assertThat(FileDifferences.entry("/bin", new FileEntry("l", "777", "0", "0", "usr/bin", null),
                new FileEntry("d", "777", "1001", "0", "", null)))
                .containsExactly("/bin: type l became d", "/bin: owner 0:0 became 1001:0",
                        "/bin: link target usr/bin became ");
    }

    private static FileEntry file(String mode, String sha256) {
        return new FileEntry("f", mode, "0", "0", "", sha256);
    }
}
