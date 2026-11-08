package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.model.BaseImage;
import io.github.fiftieshousewife.dockerfile.model.Dockerfile;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import io.github.fiftieshousewife.dockerfile.model.PolicyViolation;
import io.github.fiftieshousewife.dockerfile.model.Stage;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaEmitterTest {

    private static final String MULTISTAGE = """
            ARG GO_VERSION=1.22
            FROM registry.example.com/ubi9/go-toolset:${GO_VERSION} AS build
            # build the server
            RUN --mount=type=cache,target=/root/.cache/go-build go build -o /out/server ./cmd/server
            FROM build AS test
            RUN go test ./...
            FROM registry.example.com/ubi9/ubi-minimal:9.4
            # keep the image small
            RUN microdnf install -y tzdata && microdnf clean all
            ENV TZ=UTC LANG="en_US.UTF-8"
            LABEL team=payments
            COPY --from=build --chown=1001:0 /out/server /usr/local/bin/server
            COPY --chmod=644 --link ["config.yaml", "/etc/server/"]
            ADD https://example.com/tzdata.tar.gz /usr/share/zoneinfo/
            EXPOSE 8080 9090/udp
            USER 1001
            ENTRYPOINT ["/usr/local/bin/server"]
            CMD ["--port", "8080"]
            CMD exec /usr/local/bin/server \\
                --port 8080
            HEALTHCHECK --timeout=3s --interval=30s --retries=3 --start-period=5s CMD ["/usr/local/bin/server", "check"]
            ONBUILD COPY . /app/
            MAINTAINER payments team
            SHELL ["/bin/bash", "-c"]
            VOLUME /data
            VOLUME ["/cache"]
            STOPSIGNAL SIGTERM
            """;

    @TempDir
    Path classes;

    @Test
    void theEmittedClassCompilesAndItsDockerfilePassesTheRoundTrip() throws Exception {
        for (String original : List.of(MULTISTAGE,
                Files.readString(Path.of("../facts/src/test/resources/fixtures/dockerfiles/python-311.Dockerfile")))) {
            Conversion conversion = Convert.of(Parsed.of(original));

            Dockerfile emitted = run(JavaEmitter.emit("com.example.images", "ServiceImage", conversion, List.of()));

            assertThat(emitted).satisfies(e -> assertThat(e).isEqualTo(conversion.model()),
                    e -> assertThat(RoundTrip.check(original, e).passed()).isTrue());
        }
    }

    @Test
    void commentsInTheOriginalBecomeLineCommentsBeforeTheirInstruction() {
        String source = JavaEmitter.emit("com.example.images", "ServiceImage", Convert.of(Parsed.of(MULTISTAGE)),
                List.of());

        assertThat(source).containsSubsequence("// build the server", "\"go build -o /out/server ./cmd/server\")",
                "// keep the image small", ".install(");
    }

    @Test
    void eachPolicyBreachBecomesATodoAtTheTopOfTheClass() {
        String source = JavaEmitter.emit("com.example.images", "ServiceImage", Convert.of(Parsed.of(MULTISTAGE)),
                List.of(new PolicyViolation.Breach("R1", "base registry.example.com/ubi9/ubi-minimal is not pinned")));

        assertThat(source).containsSubsequence("// TODO(R1): base registry.example.com/ubi9/ubi-minimal is not pinned",
                "public final class ServiceImage");
    }

    @Test
    void aBreachMessageHoldingDollarSignsIsWrittenAsItIs() {
        String source = JavaEmitter.emit("com.example.images", "ServiceImage", Convert.of(Parsed.of(MULTISTAGE)),
                List.of(new PolicyViolation.Breach("H1", "install $INSTALL_PKGS without a clean")));

        assertThat(source).contains("// TODO(H1): install $INSTALL_PKGS without a clean");
    }

    @Test
    void fromWritesABaseImageOrAnEarlierStage() {
        Stage onBase = new Stage("build", new BaseImage("ubi9", "9.4", null), null, List.of());
        Stage onStage = new Stage(null, null, "build", List.of());

        assertThat(List.of(onBase, onStage)).map(stage -> JavaEmitter.from(stage).toString()).containsExactly(
                "\n.thenStage(\"build\", new io.github.fiftieshousewife.dockerfile.model.BaseImage(\"ubi9\", "
                        + "\"9.4\", null))",
                "\n.thenStageFrom(null, \"build\")");
    }

    @Test
    void stagePutsEachInstructionsCommentsBeforeIt() {
        Stage stage = new Stage(null, null, "build", List.of(new Instruction.User("1001")));

        assertThat(JavaEmitter.stage(stage, List.of(List.of("run as", "non-root"))).toString())
                .isEqualTo("\n.thenStageFrom(null, \"build\")\n// run as\n// non-root\n.user(\"1001\")");
    }

    @Test
    void todosAreOneLinePerBreachNamingItsRule() {
        assertThat(JavaEmitter.todos(List.of(new PolicyViolation.Breach("R1", "a"),
                new PolicyViolation.Breach("H1", "b")))).isEqualTo("TODO(R1): a\nTODO(H1): b");
    }

    private Dockerfile run(String source) throws Exception {
        Path folder = classes.resolve("com/example/images");
        Files.createDirectories(folder);
        Path file = folder.resolve("ServiceImage.java");
        Files.writeString(file, source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int status = compiler.run(null, null, null, "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(), file.toString());
        assertThat(status).as(source).isZero();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()},
                getClass().getClassLoader())) {
            return (Dockerfile) loader.loadClass("com.example.images.ServiceImage").getMethod("dockerfile")
                    .invoke(null);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
