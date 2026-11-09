package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StubsTest {

    @TempDir
    Path context;

    @Test
    void eachMissingSourceBecomesAnEmptyFileOrDirectoryAndIsListed() throws IOException {
        Files.writeString(context.resolve("present.txt"), "here");

        List<String> created = Stubs.create(context, List.of("present.txt", "bin/app-${TARGETOS}-${TARGETARCH}",
                "target/lib/*", "/prebuildfs/", "."), "arm64");

        assertAll(
                () -> assertThat(created).containsExactly("bin/app-linux-arm64", "target/lib/stub", "prebuildfs/"),
                () -> assertThat(context.resolve("bin/app-linux-arm64")).isEmptyFile(),
                () -> assertThat(context.resolve("target/lib/stub")).isEmptyFile(),
                () -> assertThat(context.resolve("prebuildfs/stub")).isEmptyFile(),
                () -> assertThat(context.resolve("present.txt")).hasContent("here"));
    }

    @Test
    void aGlobThatMatchesSomethingIsNotStubbed() throws IOException {
        Files.createDirectories(context.resolve("target/lib"));
        Files.writeString(context.resolve("target/lib/a.jar"), "jar");

        assertThat(Stubs.create(context, List.of("target/lib/*.jar"), "amd64")).isEmpty();
    }

    @Test
    void aSourceAlreadyThereIsNotStubbedAndAMissingOneIsWrittenAndNamed() throws IOException {
        Files.writeString(context.resolve("present.txt"), "here");

        assertThat(List.of(Stubs.stubbed(context, "present.txt"), Stubs.stubbed(context, "lib/*.jar")))
                .containsExactly(Optional.empty(), Optional.of("lib/stub.jar"));
    }

    @Test
    void aDirectoryStubHoldsAnEmptyFileNamedStub() {
        Stubs.write(context, "out/");

        assertThat(context.resolve("out/stub")).isEmptyFile();
    }

    @Test
    void aStubForASecondSourceThatTheFirstStubMatchesIsNotWrittenAgain() {
        assertThat(Stubs.create(context, List.of("lib/*.jar", "lib/*.jar"), "amd64")).containsExactly("lib/stub.jar");
    }
}
