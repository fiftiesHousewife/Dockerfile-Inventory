// The command published to Maven Central as its distribution zip, through inventory.central: everything it runs on
// is inside, so the POM names no dependencies. Central requires sources and Javadoc jars of it too: the sources of
// every module the zip ships, and for Javadoc a README, since an application has no API.

import org.gradle.api.artifacts.component.ProjectComponentIdentifier

plugins {
    application
    id("inventory.central")
}

the<CentralBundleExtension>().packaging.set("zip")

distributions.named("main") {
    contents {
        from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
    }
}

// Each module on the runtime classpath, found as Gradle resolved it, by its folder; read when the jar is built.
val shippedSources = provider {
    configurations.getByName("runtimeClasspath").incoming.resolutionResult.allComponents
        .map { it.id }.filterIsInstance<ProjectComponentIdentifier>()
        .map { rootDir.resolve(it.projectPath.removePrefix(":").replace(':', '/')).resolve("src/main/java") }
}

val distributionSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(shippedSources) { include("**/*.java") }
    includeEmptyDirs = false
}

val distributionJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(layout.projectDirectory.file("src/javadoc/README.md"))
}

publishing {
    publications {
        create<MavenPublication>("distribution") {
            artifact(tasks.named("distZip"))
            artifact(distributionSourcesJar)
            artifact(distributionJavadocJar)
            pom {
                packaging = "zip"
            }
        }
    }
}

tasks.named("verifyBundle") {
    val zip = tasks.named<Zip>("distZip").flatMap { it.archiveFile }
    val stem = tasks.named<Zip>("distZip").map { it.archiveFileName.get().removeSuffix(".zip") }
    val launcher = application.applicationName
    doLast {
        val gaps = CentralBundle.distributionGaps(zip.get().asFile, stem.get(), launcher)
        check(gaps.isEmpty()) { "the distribution lacks: $gaps" }
    }
}
