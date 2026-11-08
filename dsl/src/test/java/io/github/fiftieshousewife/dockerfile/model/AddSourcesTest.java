package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AddSourcesTest {

    @Test
    void aUrlAGitRepositoryOrATarArchiveIsWhatAddIsFor() {
        assertThat(List.of("https://example.com/tool", "http://x/y.bin", "git@github.com:o/r.git",
                "https://github.com/o/r.git#v1", "app.tar", "app.tar.gz", "app.tgz", "rootfs.tar.xz", "a.tar.bz2",
                "a.tar.zst"))
                .allMatch(AddSources::addable);
    }

    @Test
    void aPlainLocalFileOrAZipIsNot() {
        assertThat(List.of("app.jar", "run.sh", "src/", ".", "app.zip", "config.tar.json"))
                .noneMatch(AddSources::addable);
    }
}
