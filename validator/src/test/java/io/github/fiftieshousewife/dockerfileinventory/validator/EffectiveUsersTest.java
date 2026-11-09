package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EffectiveUsersTest {

    @Test
    void eachDockerfileGetsTheUserItsCreditedImagesRunAsAndNotThoseOfImagesItIsOnlyACandidateFor(@TempDir Path lineage)
            throws IOException {
        Files.writeString(lineage.resolve("links.jsonl"), """
                {"configDigest":"sha256:app","tier":"PROBABLE","blobShas":["app-df"],"credited":true}
                {"configDigest":"sha256:near","tier":"WEAK","blobShas":["near-df"],"credited":false}
                """);
        Files.writeString(lineage.resolve("effective.jsonl"), """
                {"configDigest":"sha256:app","user":{"value":"1001","setBy":{"kind":"ExternalBase","path":"ubi9/s2i"}}}
                {"configDigest":"sha256:near","user":{"value":"0","setBy":{"kind":"Unknown"}}}
                """);

        assertThat(EffectiveUsers.read(lineage))
                .containsExactly(Map.entry("app-df", new EffectiveUser("1001", "the base ubi9/s2i")));
    }

    @Test
    void aDockerfileWhoseImagesRunAsDifferentUsersGetsTheWorse(@TempDir Path lineage) throws IOException {
        Files.writeString(lineage.resolve("links.jsonl"), """
                {"configDigest":"sha256:old","tier":"PROBABLE","blobShas":["app-df"],"credited":true}
                {"configDigest":"sha256:new","tier":"PROBABLE","blobShas":["app-df"],"credited":true}
                """);
        Files.writeString(lineage.resolve("effective.jsonl"), """
                {"configDigest":"sha256:new","user":{"value":"1001","setBy":{"kind":"Unknown"}}}
                {"configDigest":"sha256:old","user":{"value":"0","setBy":{"kind":"Unknown"}}}
                """);

        assertThat(EffectiveUsers.read(lineage)).containsEntry("app-df", new EffectiveUser("0", null));
    }
}
