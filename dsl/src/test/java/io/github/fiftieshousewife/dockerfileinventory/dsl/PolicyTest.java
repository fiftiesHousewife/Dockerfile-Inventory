package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolicyTest {

    @TempDir
    Path dir;

    @Test
    void readSkipsTheHeaderAndBlankLines() throws IOException {
        Path csv = Files.writeString(dir.resolve("allow.csv"), "imagePath,tagPrefix\n\nubi9/ubi, 9\nalpine\n");

        assertThat(Policy.read(csv).approved()).containsExactly(
                new Policy.Approved("ubi9/ubi", "9"), new Policy.Approved("alpine", ""));
    }

    @Test
    void readOfAMissingFileFails() {
        assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> Policy.read(dir.resolve("none")));
    }

    @Test
    void approvedTakesAMissingPrefixAsEveryTag() {
        assertThat(Policy.approved(" ubi9/ubi , 9.4 ")).isEqualTo(new Policy.Approved("ubi9/ubi", "9.4"));
        assertThat(Policy.approved("alpine")).isEqualTo(new Policy.Approved("alpine", ""));
    }

    @Test
    void approvesMatchesThePathWithoutRegistryAndTheTagByPrefix() {
        Policy policy = Policy.of(List.of(new Policy.Approved("ubi9/ubi", "9")));

        assertThat(policy.approves(new BaseImage("registry.example.com/ubi9/ubi", "9.4", null))).isTrue();
        assertThat(policy.approves(new BaseImage("ubi9/ubi", null, null))).isFalse();
        assertThat(policy.approves(new BaseImage("ubi9/ubi-minimal", "9.4", null))).isFalse();
    }

    @Test
    void coversATagOnAVersionBoundaryOnly() {
        assertThat(Policy.covers("", "anything")).isTrue();
        assertThat(Policy.covers("9", "9")).isTrue();
        assertThat(Policy.covers("9", "9.4")).isTrue();
        assertThat(Policy.covers("9", "9-1100")).isTrue();
        assertThat(Policy.covers("9", "91")).isFalse();
    }

    @Test
    void pathDropsAFirstSegmentOnlyWhenItIsARegistryHost() {
        assertThat(Policy.path("registry.example.com/ubi9/ubi")).isEqualTo("ubi9/ubi");
        assertThat(Policy.path("localhost:5000/app")).isEqualTo("app");
        assertThat(Policy.path("localhost/app")).isEqualTo("app");
        assertThat(Policy.path("library/alpine")).isEqualTo("library/alpine");
        assertThat(Policy.path("alpine")).isEqualTo("alpine");
    }
}
