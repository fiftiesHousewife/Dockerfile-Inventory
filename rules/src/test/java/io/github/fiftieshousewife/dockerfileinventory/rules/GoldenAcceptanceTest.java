package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GoldenAcceptanceTest {

    private static final Path GOLDEN = Path.of("../dsl/src/test/resources/golden");
    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

    static Stream<Path> goldens() throws IOException {
        try (Stream<Path> files = Files.list(GOLDEN)) {
            return files.filter(f -> f.toString().endsWith(".Dockerfile")).sorted().toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("goldens")
    void aGoldenDockerfilePassesEveryPerFileRuleButTheReportOnlyI1(Path golden) throws IOException {
        List<Rule> rules = List.of(new UnpinnedBase(), new UncleanedInstall(), new ShellEntrypoint(),
                new Unparseable(), new SecretValue(), new PipeToShell(), new AdjacentRuns(), new MissingOciLabels(),
                new UnapprovedBase(BaseList.read(FIXTURES.resolve("allow.csv"))),
                new EndOfLifeBase(BaseList.read(FIXTURES.resolve("eol.csv"))));

        assertThat(new RuleRun(rules).run(List.of(Subjects.of(Files.readString(golden))))).isEmpty();
    }
}
