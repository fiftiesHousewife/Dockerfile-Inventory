import java.io.ByteArrayOutputStream
import java.net.http.HttpClient
import java.net.http.HttpResponse
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

// Uploads a library's signed bundle to the Central Portal and tags the release; credentials come from the
// environment only.
abstract class CentralReleaseTask @Inject constructor(private val exec: ExecOperations) : DefaultTask() {

    @get:InputFile
    abstract val bundle: RegularFileProperty

    @get:InputDirectory
    abstract val signedFiles: DirectoryProperty

    @get:Input
    abstract val artifact: Property<String>

    @get:Input
    abstract val version: Property<String>

    @TaskAction
    fun release() {
        val environment = System.getenv()
        val tag = CentralRelease.tag(artifact.get(), version.get())
        val refusals = CentralRelease.refusals(environment, git("status", "--porcelain").isBlank(), version.get(),
            git("tag", "--list", tag).isNotBlank())
        if (refusals.isNotEmpty()) {
            throw GradleException("not released: " + refusals.joinToString("; "))
        }
        if (signedFiles.get().asFileTree.matching { include("**/*.asc") }.isEmpty) {
            throw GradleException("not released: the bundle has no signatures; was SIGNING_KEY read?")
        }
        val response = HttpClient.newHttpClient().send(CentralRelease.upload(bundle.get().asFile.toPath(),
            "${artifact.get()}-${version.get()}", environment.getValue("CENTRAL_TOKEN_USER"),
            environment.getValue("CENTRAL_TOKEN_PASSWORD")), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() / 100 != 2) {
            throw GradleException("the Central Portal refused the upload: ${response.statusCode()} ${response.body()}")
        }
        git("tag", "-a", tag, "-m", "${artifact.get()} ${version.get()}")
        logger.lifecycle("uploaded: deployment ${response.body()}; publish it at " +
            "https://central.sonatype.com/publishing/deployments, then git push origin $tag")
    }

    private fun git(vararg arguments: String): String {
        val output = ByteArrayOutputStream()
        exec.exec {
            commandLine(listOf("git") + arguments)
            standardOutput = output
        }
        return output.toString()
    }
}
