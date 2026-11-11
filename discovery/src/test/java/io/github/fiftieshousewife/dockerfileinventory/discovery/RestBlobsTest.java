package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RestBlobsTest {

    @Test
    void asksForTheBlobInItsRepository() {
        assertThat(RestBlobs.path("api/v3/", new BlobRequest("payments/ledger-api", "aaaa")))
                .isEqualTo("api/v3/repos/payments/ledger-api/git/blobs/aaaa");
    }

    @Test
    void decodesTheTextAcrossGitHubsWrappedBase64() {
        assertThat(RestBlobs.blob("aaaa", blob("rest-blob-text.json"))).isEqualTo(new BlobText("aaaa",
                "FROM alpine:3.20\n# a comment long enough that GitHub wraps its base64 across lines\n"
                        + "RUN apk add --no-cache curl\n", false, false));
    }

    @Test
    void callsABlobWithANulByteBinary() {
        assertThat(RestBlobs.blob("bbbb", blob("rest-blob-binary.json")))
                .isEqualTo(new BlobText("bbbb", null, false, true));
    }

    @Test
    void readsTextThatIsNotUtf8AsGitDoesWithReplacementCharacters() {
        assertThat(RestBlobs.blob("cccc", blob("rest-blob-latin1.json")))
                .isEqualTo(new BlobText("cccc", "FROM alpine\nLABEL maintainer=\"Jos\uFFFD\"\n", false, false));
    }

    @Test
    void aNulPastTheFirst8000BytesLeavesTheFileText() {
        byte[] bytes = new byte[8001];
        Arrays.fill(bytes, (byte) 'a');
        bytes[8000] = 0;
        assertThat(RestBlobs.binary(bytes)).isFalse();
    }

    private static JsonNode blob(String fixture) {
        return new ObjectMapper().readTree(RecordedGitHub.fixture(fixture));
    }
}
