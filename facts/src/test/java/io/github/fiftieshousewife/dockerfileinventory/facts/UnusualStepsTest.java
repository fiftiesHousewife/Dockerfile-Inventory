package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnusualStepsTest {

    @Test
    void everydayInstructionsInTheirEverydayFormsAreNotUnusual() {
        assertThat(List.of("RUN make", "COPY a /b", "COPY --from=build --chown=1001:0 /a /b", "ENV A=1", "ARG B",
                "LABEL x=y", "WORKDIR /a", "USER 1001", "EXPOSE 8080", "ENTRYPOINT [\"a\"]", "CMD b"))
                .map(UnusualSteps::of).containsOnly(Optional.empty());
    }

    @Test
    void eachUnusualStepIsNamedByItsInstructionAndForm() {
        assertThat(List.of("ADD https://example.com/a.tgz /opt/", "ADD app.tar /opt/", "ONBUILD COPY . /app",
                "SHELL [\"/bin/bash\", \"-c\"]", "HEALTHCHECK CMD true", "VOLUME /data", "STOPSIGNAL SIGQUIT",
                "MAINTAINER me", "RUN [\"make\"]", "RUN <<EOF", "RUN --mount=type=cache,target=/c make",
                "RUN --network=none make", "COPY --link a /b", "COPY <<EOF /etc/a", "COPY --exclude=*.md . /src"))
                .map(line -> UnusualSteps.of(line).orElse(null)).containsExactly("ADD of a URL", "ADD",
                        "ONBUILD", "SHELL", "HEALTHCHECK", "VOLUME", "STOPSIGNAL", "MAINTAINER", "RUN in exec form",
                        "RUN with a heredoc", "RUN --mount", "RUN --network", "COPY --link", "COPY with a heredoc",
                        "COPY --exclude");
    }

    @Test
    void theFirstUnusualFlagIsNamedWithoutItsValue() {
        assertThat(UnusualSteps.flagged("RUN", List.of("--mount=type=cache", "--network=none"),
                List.of("--network", "--mount"))).contains("RUN --mount");
    }
}
