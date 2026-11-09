package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JcrContainersTest {

    // Docker as a list of what was asked, answering each command by its start.
    static final class Recorded implements Commands {

        final List<String> asked = new ArrayList<>();
        final Map<String, Result> answers = new HashMap<>();

        @Override
        public Result run(List<String> command, String stdin) {
            String line = String.join(" ", command);
            asked.add(line);
            return answers.entrySet().stream().filter(e -> line.startsWith(e.getKey())).map(Map.Entry::getValue)
                    .findFirst().orElse(new Result(0, ""));
        }
    }

    private final Recorded docker = new Recorded();
    private final JcrContainers containers = new JcrContainers(docker, "jcr:7.77.17", "postgres:16.4", 8082);

    @Test
    void postgresAlreadyRunningIsLeftAlone() {
        docker.answers.put("docker ps -q -f name=^inventory-postgres$", new Commands.Result(0, "abc\n"));
        docker.asked.clear();
        containers.postgres();

        assertThat(docker.asked).noneMatch(line -> line.startsWith("docker run"));
    }

    @Test
    void postgresRunsWithItsVolumeTheArtifactoryDatabaseAndTheImageAsked() {
        containers.postgres();

        assertThat(docker.asked).anyMatch(line -> line.startsWith("docker run -d --name inventory-postgres --network "
                + "inventory-demo -v inventory-postgres:/var/lib/postgresql/data") && line.endsWith("postgres:16.4"));
    }

    @Test
    void jcrOfAnotherVersionIsRefusedRatherThanItsDataRemoved() {
        docker.answers.put("docker inspect -f {{.Config.Image}} inventory-jcr", new Commands.Result(0,
                "jcr:7.191\n"));

        assertThatIllegalStateException().isThrownBy(containers::jcr)
                .withMessageStartingWith("JCR runs jcr:7.191, not jcr:7.77.17");
    }

    @Test
    void jcrRunsOnPostgresqlPublishedOnThePortAsked() throws IOException {
        int free;
        try (ServerSocket socket = new ServerSocket(0)) {
            free = socket.getLocalPort();
        }

        new JcrContainers(docker, "jcr:7.77.17", "postgres:16.4", free).jcr();

        assertThat(docker.asked).anyMatch(line -> line.startsWith("docker run -d --name inventory-jcr --network "
                + "inventory-demo -p " + free + ":8082") && line.contains("JF_SHARED_DATABASE_TYPE=postgresql")
                && line.endsWith("jcr:7.77.17"));
    }

    @Test
    void theSetupWizardIsAskedForItsDockerRepositoriesThroughItsImportFileThenJcrRestarted() {
        containers.askWizardForDocker();

        assertThat(docker.asked).containsSubsequence("docker exec -i inventory-jcr sh -c cat > "
                + "/var/opt/jfrog/artifactory/etc/artifactory/artifactory.config.import.yml",
                "docker restart inventory-jcr");
    }

    @Test
    void dockersMemoryIsReadInWholeGigabytes() {
        docker.answers.put("docker info --format {{.MemTotal}}", new Commands.Result(0, "8327098368\n"));

        assertThat(containers.memoryGb()).isEqualTo(8);
    }
}
