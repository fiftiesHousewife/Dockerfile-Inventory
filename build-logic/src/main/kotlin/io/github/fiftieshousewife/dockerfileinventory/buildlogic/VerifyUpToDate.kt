package io.github.fiftieshousewife.dockerfileinventory.buildlogic

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.util.GradleVersion

/** Fails when a dependency, plugin, Gradle or the JDK is behind its newest stable release. */
@UntrackedTask(because = "Whether anything is outdated depends on releases published since the last run")
abstract class VerifyUpToDate : DefaultTask() {

    @get:InputFile
    abstract val report: RegularFileProperty

    @get:Input
    abstract val javaRelease: Property<Int>

    @TaskAction
    fun verify() {
        val behind = UpToDate.outdatedDependencies(report.get().asFile.readText()) + listOfNotNull(
            UpToDate.gradleBehind(GradleVersion.current().version, fetch("https://services.gradle.org/versions/current")),
            UpToDate.jdkBehind(javaRelease.get(), fetch("https://api.adoptium.net/v3/info/available_releases")),
        )
        if (behind.isNotEmpty()) {
            throw GradleException("Behind the newest stable release (${behind.size}):\n  - " + behind.joinToString("\n  - "))
        }
    }

    /**
     * Any failure fails the build: a check that cannot look must not pass. Uses the JVM's standard proxy
     * properties, including https.proxyUser and https.proxyPassword, which plain URL connections ignore.
     */
    private fun fetch(url: String): String {
        val user = System.getProperty("https.proxyUser")
        val password = System.getProperty("https.proxyPassword")
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).apply {
            if (user != null && password != null) {
                authenticator(object : Authenticator() {
                    override fun getPasswordAuthentication() =
                        if (requestorType == RequestorType.PROXY) PasswordAuthentication(user, password.toCharArray()) else null
                })
            }
        }.build()
        val response = client.send(HttpRequest.newBuilder(URI(url)).build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw GradleException("Up-to-date check could not read $url: HTTP ${response.statusCode()}")
        }
        return response.body()
    }
}
