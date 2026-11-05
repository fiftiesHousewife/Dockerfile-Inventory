// Fails `check` when anything the build pins is behind its newest stable release: every module's libraries,
// BOMs and tool versions (JaCoCo, SpotBugs), the Gradle plugins build-logic uses, Gradle itself, and the JDK
// against the newest long-term-support release. Applied to the root project only; the versions plugin then
// checks every project. Needs the network: work offline with `-x verifyUpToDate`.

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import io.github.fiftieshousewife.dockerfileinventory.buildlogic.VerifyUpToDate

plugins {
    base
    id("io.github.ben-manes.versions")
}

val libs = versionCatalogs.named("libs")
fun lib(alias: String) = libs.findLibrary(alias).orElseThrow { IllegalStateException("missing library: $alias") }

// Plugins on build-logic's classpath, and the settings plugin, are no project's dependencies; declared here,
// the check sees them.
val buildLogicPlugins = configurations.create("buildLogicPlugins") {
    isCanBeConsumed = false
}
dependencies {
    add(buildLogicPlugins.name, lib("spotbugs-gradle-plugin"))
    add(buildLogicPlugins.name, lib("gradle-versions-plugin"))
    add(buildLogicPlugins.name, lib("foojay-resolver"))
}

val prerelease = Regex("(?i).*[.-](alpha|beta|rc|cr|m|milestone|preview|snapshot|ea|dev)[.\\d-]*$")

val dependencyUpdates = tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    rejectVersionIf { prerelease.matches(candidate.version) }
    // The plugin reports a failed Gradle lookup as "UP-TO-DATE"; VerifyUpToDate checks Gradle itself.
    checkForGradleUpdate = false
    outputFormatter = "json"
    outputDir = layout.buildDirectory.dir("dependencyUpdates").get().asFile.path
    reportfileName = "report"
}

val verifyUpToDate = tasks.register<VerifyUpToDate>("verifyUpToDate") {
    description = "Fails if a dependency, plugin, Gradle or the JDK is behind its newest stable release."
    group = "verification"
    dependsOn(dependencyUpdates)
    report.set(layout.buildDirectory.file("dependencyUpdates/report.json"))
    javaRelease.set(providers.gradleProperty("inventory.javaRelease").map { it.toInt() })
}

tasks.named("check") {
    dependsOn(verifyUpToDate)
}
