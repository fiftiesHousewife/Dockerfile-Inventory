package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LineageMainTest {

    @Test
    void withAnArtifactoryUrlTheInventoryAsksArtifactoryForItsRepositoriesFirst(@TempDir Path out,
                                                                                @TempDir Path facts,
                                                                                @TempDir Path discovery)
            throws IOException {
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            LineageMain.run(Map.of("OUT_DIR", out.toString(), "FACTS_DIR", facts.toString(),
                    "DISCOVERY_DIR", discovery.toString(), "ARTIFACTORY_TOKEN", "t0ken",
                    "ARTIFACTORY_MAX_REQUESTS_PER_SECOND", "100",
                    "ARTIFACTORY_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/artifactory/"));
        } finally {
            server.stop(0);
        }

        assertThat(requests).containsExactly("/artifactory/api/repositories?type=local&packageType=docker");
    }

    @Test
    void aRequiredSettingIsItsValue() {
        assertThat(LineageMain.required(Map.of("OUT_DIR", "/out"), "OUT_DIR")).isEqualTo("/out");
    }

    @Test
    void aMissingOrBlankRequiredSettingIsAnErrorNamingIt() {
        assertThatIllegalStateException().isThrownBy(() -> LineageMain.required(Map.of(), "OUT_DIR"))
                .withMessage("OUT_DIR is not set");
        assertThatIllegalStateException().isThrownBy(() -> LineageMain.required(Map.of("OUT_DIR", " "), "OUT_DIR"))
                .withMessage("OUT_DIR is not set");
    }

    @Test
    void withoutAnArtifactoryUrlOnlyTheAnalysisRunsOnWhatTheOutputDirectoryHolds(@TempDir Path out,
                                                                                @TempDir Path facts,
                                                                                @TempDir Path discovery) {
        LineageMain.run(Map.of("OUT_DIR", out.toString(), "FACTS_DIR", facts.toString(),
                "DISCOVERY_DIR", discovery.toString(), "ARTIFACTORY_URL", ""));

        assertThat(out.resolve("rates.json")).exists();
        assertThat(out.resolve("images.jsonl")).doesNotExist();
    }

    @Test
    void theLinkThresholdAndMarginAreReadFromTheEnvironment() {
        assertThat(LineageMain.linkRule(Map.of("LINK_THRESHOLD", "0.9", "LINK_MARGIN", "0.1")))
                .isEqualTo(new LinkRule(0.9, 0.1));
    }

    @Test
    void withoutTheLinkSettingsTheRuleIsTheDefault() {
        assertThat(LineageMain.linkRule(Map.of())).isEqualTo(LinkRule.DEFAULT);
    }

    @Test
    void aLinkSettingThatIsNotAShareBetweenZeroAndOneIsRefusedByName() {
        assertThatIllegalStateException().isThrownBy(() -> LineageMain.linkRule(Map.of("LINK_MARGIN", "5")))
                .withMessage("LINK_MARGIN must be a number from 0 to 1, not 5");
    }
}
