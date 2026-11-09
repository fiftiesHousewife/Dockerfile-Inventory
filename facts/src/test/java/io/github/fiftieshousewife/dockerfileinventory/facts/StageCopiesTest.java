package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StageCopiesTest {

    @Test
    void eachCopyOrAddSaysWhereItsFilesComeFromAndWithWhatOwnerAndMode() {
        Set<String> stages = Set.of("build");

        assertThat(List.of("COPY --from=build --chown=1001:0 --chmod=755 /out/app /usr/local/bin/",
                        "COPY --from=docker.io/library/busybox:1.36 /bin/sh /bin/sh",
                        "COPY [\"with space\", \"/opt/\"]",
                        "ADD https://example.com/a.tgz rootfs.tar.gz /opt/",
                        "COPY <<EOF /etc/app.conf")
                .stream().map(line -> StageCopies.copy(line, stages)).toList()).containsExactly(
                new CopyFact("COPY", List.of("/out/app"), "/usr/local/bin/", "stage", "build", "1001:0", "755"),
                new CopyFact("COPY", List.of("/bin/sh"), "/bin/sh", "image", "docker.io/library/busybox:1.36", null,
                        null),
                new CopyFact("COPY", List.of("with space"), "/opt/", "context", null, null, null),
                new CopyFact("ADD", List.of("https://example.com/a.tgz", "rootfs.tar.gz"), "/opt/", "url", null, null,
                        null),
                new CopyFact("COPY", List.of(), "/etc/app.conf", "heredoc", null, null, null));
    }

    @Test
    void anOriginIsAStageByNameOrNumberAnImageAUrlOrTheContext() {
        assertAll(
                () -> assertThat(StageCopies.origin("0", List.of("/a"), Set.of())).isEqualTo("stage"),
                () -> assertThat(StageCopies.origin("golang:1.22", List.of("/a"), Set.of())).isEqualTo("image"),
                () -> assertThat(StageCopies.origin(null, List.of("git@github.com:o/r.git"), Set.of()))
                        .isEqualTo("url"),
                () -> assertThat(StageCopies.origin(null, List.of("src/"), Set.of())).isEqualTo("context"));
    }

    @Test
    void pathsAreReadFromTheJsonFormOrElseUnquotedWords() {
        assertAll(
                () -> assertThat(StageCopies.paths(List.of("[\"a\",", "\"/b\"]"))).containsExactly("a", "/b"),
                () -> assertThat(StageCopies.paths(List.of("[a", "/b"))).containsExactly("[a", "/b"),
                () -> assertThat(StageCopies.paths(List.of("\"a\"", "/b"))).containsExactly("a", "/b"));
    }

    @Test
    void theLastFlagWithThePrefixGivesItsValue() {
        assertAll(
                () -> assertThat(StageCopies.flag(List.of("--from=a", "--from=b"), "--from=")).isEqualTo("b"),
                () -> assertThat(StageCopies.flag(List.of("--chown=1"), "--from=")).isNull());
    }
}
