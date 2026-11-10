package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckoutsTest {

    @TempDir
    Path remotes;

    @TempDir
    Path root;

    private String commit;

    private Checkouts checkouts;

    @BeforeEach
    void aRepositoryWithOneCommit() throws IOException {
        assumeTrue(gitRuns(), "git is not installed");
        Path repository = Files.createDirectories(remotes.resolve("o/r.git"));
        git(repository, "init", "-q");
        Files.writeString(repository.resolve("Dockerfile"), "FROM scratch\n");
        git(repository, "add", "Dockerfile");
        git(repository, "-c", "user.name=t", "-c", "user.email=t@example.com", "-c", "commit.gpgsign=false",
                "commit", "-q", "-m", "one");
        commit = git(repository, "rev-parse", "HEAD").strip();
        checkouts = new Checkouts(root, new CloneSource("file://" + remotes.toAbsolutePath(), Optional.empty()));
    }

    @Test
    void aRepositoryIsClonedAtTheCommitUnderItsNameAndCommit() {
        Path checkout = checkouts.checkout("o/r", commit);

        assertAll(
                () -> assertThat(checkout).isEqualTo(root.resolve("o/r").resolve(commit)),
                () -> assertThat(checkout.resolve("Dockerfile")).hasContent("FROM scratch"));
    }

    @Test
    void aSecondCheckoutReusesTheCloneWithoutTheLastComparisonsUntrackedFiles() throws IOException {
        Path first = checkouts.checkout("o/r", commit);
        Files.writeString(first.resolve("stub"), "");

        Path second = checkouts.checkout("o/r", commit);

        assertAll(
                () -> assertThat(second).isEqualTo(first),
                () -> assertThat(second.resolve("stub")).doesNotExist(),
                () -> assertThat(second.resolve("Dockerfile")).exists());
    }

    private static String git(Path repository, String... arguments) {
        List<String> command = new ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(arguments));
        return Processes.output(command);
    }

    private static boolean gitRuns() {
        try {
            return Processes.output(List.of("git", "--version")).startsWith("git");
        } catch (IllegalStateException | UncheckedIOException e) {
            return false;
        }
    }
}
