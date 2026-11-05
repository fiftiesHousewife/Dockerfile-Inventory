package io.github.fiftieshousewife.dockerfileinventory.buildlogic

import groovy.json.JsonSlurper
import org.gradle.util.GradleVersion

/** The comparisons behind [VerifyUpToDate], kept free of I/O so they can be tested. Each returns what is behind. */
object UpToDate {

    /** From the versions plugin's JSON report: outdated dependencies, and any it could not resolve to check. */
    fun outdatedDependencies(reportJson: String): List<String> {
        val report = json(reportJson)
        val outdated = section(report, "outdated").map { d ->
            @Suppress("UNCHECKED_CAST")
            val available = d["available"] as Map<String, Any?>
            "${d["group"]}:${d["name"]} ${d["version"]} -> ${available["release"] ?: available["milestone"]}"
        }
        val unresolved = section(report, "unresolved").map { d -> "${d["group"]}:${d["name"]} ${d["version"]} could not be checked" }
        return outdated + unresolved
    }

    /** From services.gradle.org/versions/current. */
    fun gradleBehind(current: String, currentReleaseJson: String): String? {
        val newest = json(currentReleaseJson)["version"] as String
        return if (GradleVersion.version(current) < GradleVersion.version(newest)) {
            "Gradle $current -> $newest (./gradlew wrapper --gradle-version $newest)"
        } else {
            null
        }
    }

    /** From api.adoptium.net/v3/info/available_releases: the newest long-term-support release is the target. */
    fun jdkBehind(release: Int, availableReleasesJson: String): String? {
        val newestLts = (json(availableReleasesJson)["most_recent_lts"] as Number).toInt()
        return if (release < newestLts) "JDK $release -> $newestLts (inventory.javaRelease in gradle.properties)" else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun json(text: String) = JsonSlurper().parseText(text) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun section(report: Map<String, Any?>, name: String): List<Map<String, Any?>> =
        ((report[name] as Map<String, Any?>?)?.get("dependencies") as List<Map<String, Any?>>?).orEmpty()
}
