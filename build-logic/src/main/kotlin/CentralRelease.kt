import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.UUID

// A release to the Sonatype Central Portal, as user-managed: Central validates the bundle and waits for the owner
// to press Publish, so nothing is public until then.
object CentralRelease {

    val credentials = listOf("SIGNING_KEY", "SIGNING_PASSWORD", "CENTRAL_TOKEN_USER", "CENTRAL_TOKEN_PASSWORD")

    fun refusals(environment: Map<String, String>, treeClean: Boolean, version: String, tagExists: Boolean) =
        credentials.filter { environment[it].isNullOrBlank() }.map { "$it is not set" } +
            listOfNotNull(
                "the working tree has changes; release from a clean checkout".takeUnless { treeClean },
                "$version is not a release version".takeIf { version.isBlank() || version.endsWith("SNAPSHOT") },
                "the version is already tagged".takeIf { tagExists })

    fun tag(artifact: String, version: String) = "$artifact-v$version"

    fun upload(bundle: Path, name: String, user: String, password: String): HttpRequest {
        val boundary = UUID.randomUUID().toString()
        val token = Base64.getEncoder().encodeToString("$user:$password".toByteArray(StandardCharsets.UTF_8))
        val query = "name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&publishingType=USER_MANAGED"
        return HttpRequest.newBuilder(URI.create("https://central.sonatype.com/api/v1/publisher/upload?$query"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(bundle, boundary)))
            .build()
    }

    fun multipart(bundle: Path, boundary: String): ByteArray {
        val head = "--$boundary\r\nContent-Disposition: form-data; name=\"bundle\"; filename=\"${bundle.fileName}\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n"
        return head.toByteArray(StandardCharsets.UTF_8) + Files.readAllBytes(bundle) +
            "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
    }
}
