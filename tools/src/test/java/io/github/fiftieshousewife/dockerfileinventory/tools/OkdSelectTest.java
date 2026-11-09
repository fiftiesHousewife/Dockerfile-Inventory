package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OkdSelectTest {

    private static final String RELEASE = "5.0.0-okd-scos.5";

    @TempDir
    Path out;

    @TempDir
    Path discovery;

    private final List<String> fetched = new ArrayList<>();

    private final Quay quay = new Quay() {
        @Override
        public List<String> tags(String release) {
            return List.of(release + "-tools", release + "-oauth", release + "-tools");
        }

        @Override
        public String config(String tag) {
            fetched.add(tag);
            String repository = tag.endsWith("-tools") ? "openshift/oc" : "openshift/oauth-server";
            return "{\"config\":{\"Labels\":{\"io.openshift.build.source-location\":\"https://github.com/" + repository
                    + "\",\"io.openshift.build.commit.id\":\"c1\"}},\"history\":[{\"comment\":\"FROM quay.io/okd/"
                    + "base:9\"},{\"comment\":\"FROM oci-archive\"},{}]}";
        }
    };

    @BeforeEach
    void discovered() throws IOException {
        Files.writeString(discovery.resolve("repos.jsonl"), "{\"repository\":\"openshift/oauth-server\","
                + "\"dockerfiles\":2}\n{\"repository\":\"openshift/empty\",\"dockerfiles\":0}\n");
    }

    @Test
    void writesTheComponentsTheChoiceTheMirrorListAndTheReleaseIndex() throws IOException {
        new OkdSelect(quay, RELEASE, out, discovery, 30).run();

        assertAll(
                () -> assertThat(Files.readAllLines(out.resolve("tags.txt")))
                        .containsExactly(RELEASE + "-oauth", RELEASE + "-tools"),
                () -> assertThat(Files.readAllLines(out.resolve("images.tsv")))
                        .containsExactly(RELEASE + "-oauth\topenshift/oauth-server\tc1",
                                RELEASE + "-tools\topenshift/oc\tc1"),
                () -> assertThat(Files.readAllLines(out.resolve("chosen.tsv"))).hasSize(2),
                () -> assertThat(Files.readAllLines(out.resolve("bases.txt"))).containsExactly("quay.io/okd/base:9"),
                () -> assertThat(Files.readAllLines(out.resolve("mirror.txt"))).containsExactly(
                        "quay.io/okd/base:9 okd/base:9", "quay.io/okd/scos-content:" + RELEASE + "-tools okd/tools:"
                                + RELEASE, "quay.io/okd/scos-content:" + RELEASE + "-oauth okd/oauth:" + RELEASE),
                () -> assertThat(Files.readAllLines(out.resolve("releases.csv")))
                        .containsExactly("openshift/oauth-server,c1", "openshift/oc,c1"));
    }

    @Test
    void aConfigReadBeforeIsNotFetchedAgain() throws IOException {
        Files.createDirectories(out.resolve("configs"));
        Files.writeString(out.resolve("configs/" + RELEASE + "-tools.json"), quay.config(RELEASE + "-tools"));
        fetched.clear();

        new OkdSelect(quay, RELEASE, out, discovery, 30).run();

        assertThat(fetched).containsExactly(RELEASE + "-oauth");
    }

    @Test
    void theRepositoriesDiscoveryScannedAreThoseWithADockerfile() {
        assertThat(OkdSelect.scanned(discovery)).containsExactly("openshift/oauth-server");
    }

    @Test
    void anImagesBasesAreTheFromCommentsInItsHistoryButAnOciArchive() {
        assertThat(OkdSelect.bases(Json.MAPPER.readTree(quay.config(RELEASE + "-tools"))))
                .isEqualTo(List.of("quay.io/okd/base:9"));
    }
}
