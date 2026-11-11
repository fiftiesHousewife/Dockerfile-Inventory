// A module published to Maven Central: one publication with the POM Central requires, signatures when a key is in
// the environment. `check` writes the bundle to build/central (unsigned without a key) and verifies it, so what a
// release uploads is tested on every build; releaseToCentral uploads it. inventory.published (a library) and
// inventory.distributed (the command) say what the publication holds.

plugins {
    `maven-publish`
    signing
}

val bundleSettings = extensions.create<CentralBundleExtension>("centralBundle")
bundleSettings.packaging.convention("jar")

val central = layout.buildDirectory.dir("central")

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            url.set("https://github.com/fiftiesHousewife/Dockerfile-Inventory")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("fiftiesHousewife")
                    name.set("Pippa Newbold")
                    url.set("https://github.com/fiftiesHousewife")
                }
            }
            scm {
                url.set("https://github.com/fiftiesHousewife/Dockerfile-Inventory")
                connection.set("scm:git:https://github.com/fiftiesHousewife/Dockerfile-Inventory.git")
            }
        }
    }
    repositories {
        maven {
            name = "central"
            url = uri(central)
        }
    }
}

val signingKey = providers.environmentVariable("SIGNING_KEY")
signing {
    isRequired = signingKey.isPresent
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), providers.environmentVariable("SIGNING_PASSWORD").orNull)
        sign(publishing.publications)
    }
}

// The one publication's artifactId, so the bundle is checked and named as the POM names it.
val artifactId = provider { publishing.publications.withType<MavenPublication>().single().artifactId }

val verifyBundle by tasks.registering {
    group = "verification"
    description = "Checks the Maven Central bundle in build/central has every file Central requires."
    dependsOn("publishAllPublicationsToCentralRepository")
    val bundle = central
    val path = provider { "${project.group}".replace('.', '/') + "/" + artifactId.get() + "/" + version }
    val stem = provider { "${artifactId.get()}-$version" }
    val packaging = bundleSettings.packaging
    doLast {
        val dir = bundle.get().dir(path.get()).asFile
        val missing = CentralBundle.missing(dir, stem.get(), packaging.get())
        check(missing.isEmpty()) { "the Central bundle in $dir lacks: $missing" }
        val gaps = CentralBundle.pomGaps(dir.resolve("${stem.get()}.pom").readText())
        check(gaps.isEmpty()) { "the POM lacks $gaps, which Maven Central requires" }
    }
}

// The bundle Central takes: the signed files, checksums and POM, without Gradle's maven-metadata files.
val centralBundleZip by tasks.registering(Zip::class) {
    group = "publishing"
    description = "Zips the verified Central bundle; releaseToCentral uploads it."
    dependsOn(verifyBundle)
    from(central) { exclude("**/maven-metadata.xml*") }
    archiveFileName.set(artifactId.map { "$it-$version-bundle.zip" })
    destinationDirectory.set(layout.buildDirectory)
}

tasks.register<CentralReleaseTask>("releaseToCentral") {
    group = "publishing"
    description = "Uploads the signed bundle to the Central Portal (published there by hand) and tags the release."
    bundle.set(centralBundleZip.flatMap { it.archiveFile })
    signedFiles.set(central)
    artifact.set(artifactId)
    version.set(provider { project.version.toString() })
    outputs.upToDateWhen { false }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(centralBundleZip)
}
