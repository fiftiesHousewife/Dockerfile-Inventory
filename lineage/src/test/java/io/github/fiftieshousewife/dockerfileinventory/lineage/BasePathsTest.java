package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static io.github.fiftieshousewife.dockerfileinventory.lineage.LinksTest.dockerfile;
import static io.github.fiftieshousewife.dockerfileinventory.lineage.LinksTest.image;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BasePathsTest {

    @Test
    void theBasePathIsTheImagePathOfTheLastFromCommentInTheHistory() {
        assertThat(BasePaths.basePath(image("app", "FROM registry.example.com/team/base:1", "RUN make")))
                .isEqualTo("team/base");
    }

    @Test
    void anImageWithNoFromCommentHasNoBasePath() {
        assertThat(BasePaths.basePath(new Resolved.Node("bare", new ImageConfig(List.of("L1"),
                List.of(new HistoryStep("/bin/sh -c make", null, false, null)), null, Map.of())))).isNull();
    }

    @Test
    void theDockerfilesOnABaseAreThoseNamingItWithOrWithoutTheRepositoryKey() {
        Dockerfile plain = dockerfile("plain", "team/base", false);
        Dockerfile keyed = dockerfile("keyed", "docker-local/team/base", false);

        assertThat(BasePaths.on("team/base", List.of(plain, keyed, dockerfile("other", "other/base", false),
                dockerfile("none", null, false)))).containsExactly(plain, keyed);
    }

    @Test
    void pathsAreTheSameWithOrWithoutALeadingRepositoryKeyOnEitherSide() {
        assertAll(
                () -> assertThat(BasePaths.samePath("team/base", "team/base")).isTrue(),
                () -> assertThat(BasePaths.samePath("docker-local/team/base", "team/base")).isTrue(),
                () -> assertThat(BasePaths.samePath("team/base", "docker-local/team/base")).isTrue(),
                () -> assertThat(BasePaths.samePath("team/base", "team/other")).isFalse());
    }

    @Test
    void aDockerfileIsOnAHeldBaseWhenArtifactoryHasItsPathWithOrWithoutTheRepositoryKey() {
        Set<String> names = Set.of("team/base");

        assertAll(
                () -> assertThat(BasePaths.heldByArtifactory(dockerfile("a", "team/base", false), names)).isTrue(),
                () -> assertThat(BasePaths.heldByArtifactory(dockerfile("b", "docker-local/team/base", false), names))
                        .isTrue(),
                () -> assertThat(BasePaths.heldByArtifactory(dockerfile("c", "ubi9", false), names)).isFalse(),
                () -> assertThat(BasePaths.heldByArtifactory(dockerfile("d", null, false), names)).isFalse());
    }
}
