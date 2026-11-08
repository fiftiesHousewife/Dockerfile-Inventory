import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CentralReleaseTest {

    private val credentials = mapOf("SIGNING_KEY" to "k", "SIGNING_PASSWORD" to "p", "CENTRAL_TOKEN_USER" to "u",
        "CENTRAL_TOKEN_PASSWORD" to "t")

    @Test
    fun aReleaseWithEverythingInPlaceIsNotRefused() {
        assertThat(CentralRelease.refusals(credentials, treeClean = true, version = "1.0.0", tagExists = false))
            .isEmpty()
    }

    @Test
    fun eachMissingCredentialIsNamed() {
        assertThat(CentralRelease.refusals(credentials - "SIGNING_KEY" - "CENTRAL_TOKEN_PASSWORD", true, "1.0.0", false))
            .containsExactly("SIGNING_KEY is not set", "CENTRAL_TOKEN_PASSWORD is not set")
    }

    @Test
    fun aChangedTreeASnapshotAndAnExistingTagAreRefused() {
        assertThat(CentralRelease.refusals(credentials, treeClean = false, version = "0.2.0-SNAPSHOT", tagExists = true))
            .containsExactly("the working tree has changes; release from a clean checkout",
                "0.2.0-SNAPSHOT is not a release version", "the version is already tagged")
    }

    @Test
    fun aReleaseIsTaggedWithItsArtifactAndVersion() {
        assertThat(CentralRelease.tag("dockerfile-model", "0.1.0")).isEqualTo("dockerfile-model-v0.1.0")
    }

    @Test
    fun theUploadIsAUserManagedDeploymentWithTheBundleAndABearerToken(@TempDir dir: Path) {
        val bundle = Files.writeString(dir.resolve("bundle.zip"), "zip")

        val request = CentralRelease.upload(bundle, "dockerfile-model-0.1.0", "u", "t")

        assertAll(
            { assertThat(request.uri().toString()).isEqualTo("https://central.sonatype.com/api/v1/publisher/upload" +
                "?name=dockerfile-model-0.1.0&publishingType=USER_MANAGED") },
            { assertThat(request.headers().firstValue("Authorization")).hasValue("Bearer dTp0") },
            { assertThat(request.headers().firstValue("Content-Type").get()).startsWith("multipart/form-data; boundary=") },
            { assertThat(request.method()).isEqualTo("POST") })
    }

    @Test
    fun theUploadBodyCarriesTheBundleAsTheBundleField(@TempDir dir: Path) {
        val bundle = Files.writeString(dir.resolve("bundle.zip"), "zip-bytes")

        val body = String(CentralRelease.multipart(bundle, "B"))

        assertThat(body).isEqualTo("--B\r\nContent-Disposition: form-data; name=\"bundle\"; filename=\"bundle.zip\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\nzip-bytes\r\n--B--\r\n")
    }
}
