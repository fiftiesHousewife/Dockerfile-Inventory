// A module published to Maven Central: jar, sources and Javadoc jars, a complete POM, signatures when a key is in
// the environment. `check` writes the bundle to build/central (unsigned without a key) and verifies it, so what a
// release uploads is tested on every build; scripts/release-parser.sh uploads it.

import java.util.zip.ZipFile

plugins {
    `java-library`
    `maven-publish`
    signing
}

java {
    withSourcesJar()
    withJavadocJar()
}

// A module with its own LICENSE and NOTICE (the parser, a fork) ships those; the others ship the repository's.
tasks.named<Jar>("jar") {
    listOf("LICENSE", "NOTICE").forEach { name ->
        val own = layout.projectDirectory.file(name).asFile
        from(if (own.isFile) own else rootProject.file(name)) { into("META-INF") }
    }
}

// Libraries are built for the LTS users run, and tested on it; the pipeline's own modules stay on the newest.
val libraryRelease = providers.gradleProperty("inventory.libraryRelease").orElse("21").get().toInt()
tasks.withType<JavaCompile>().configureEach {
    options.release.set(libraryRelease)
}
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(libraryRelease)) })
}

// Javadoc that is wrong (a broken link, bad HTML) fails the build; a missing @param or @return does not, since a
// record's component names say what its parameters are.
tasks.withType<Javadoc>().configureEach {
    val doclet = options as StandardJavadocDocletOptions
    doclet.addStringOption("Xdoclint:all,-missing", "-quiet")
    doclet.addBooleanOption("Werror", true)
}

val central = layout.buildDirectory.dir("central")

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            versionMapping {
                allVariants {
                    fromResolutionResult()
                }
            }
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
        sign(publishing.publications["library"])
    }
}

val verifyBundle by tasks.registering {
    group = "verification"
    description = "Checks the Maven Central bundle in build/central has every file Central requires."
    dependsOn("publishLibraryPublicationToCentralRepository")
    val bundle = central
    val artifact = provider { "${project.group}".replace('.', '/') + "/" + base.archivesName.get() + "/" + version }
    val name = base.archivesName
    val ver = provider { version.toString() }
    doLast {
        val dir = bundle.get().dir(artifact.get()).asFile
        val stem = "${name.get()}-${ver.get()}"
        val required = listOf(".jar", "-sources.jar", "-javadoc.jar", ".pom").map { stem + it }
        val missing = required.filterNot { dir.resolve(it).isFile } +
            required.flatMap { listOf("$it.md5", "$it.sha1") }.filterNot { dir.resolve(it).isFile }
        check(missing.isEmpty()) { "the Central bundle in $dir lacks: $missing" }
        val pom = dir.resolve("$stem.pom").readText()
        listOf("<name>", "<description>", "<url>", "<licenses>", "<developers>", "<scm>").forEach {
            check(pom.contains(it)) { "the POM lacks $it, which Maven Central requires" }
        }
        ZipFile(dir.resolve("$stem.jar")).use { jar ->
            listOf("META-INF/LICENSE", "META-INF/NOTICE").forEach {
                check(jar.getEntry(it) != null) { "the jar lacks $it" }
            }
            val manifest = jar.getInputStream(jar.getEntry("META-INF/MANIFEST.MF")).reader().readText()
            check(jar.getEntry("module-info.class") != null || manifest.contains("Automatic-Module-Name:")) {
                "the jar is neither a module nor names one (Automatic-Module-Name)"
            }
        }
    }
}

// The bundle Central takes: the signed files, checksums and POM, without Gradle's maven-metadata files.
val centralBundleZip by tasks.registering(Zip::class) {
    group = "publishing"
    description = "Zips the verified Central bundle; releaseToCentral uploads it."
    dependsOn(verifyBundle)
    from(central) { exclude("**/maven-metadata.xml*") }
    archiveFileName.set(base.archivesName.map { "$it-$version-bundle.zip" })
    destinationDirectory.set(layout.buildDirectory)
}

tasks.register<CentralReleaseTask>("releaseToCentral") {
    group = "publishing"
    description = "Uploads the signed bundle to the Central Portal (published there by hand) and tags the release."
    bundle.set(centralBundleZip.flatMap { it.archiveFile })
    signedFiles.set(central)
    artifact.set(base.archivesName)
    version.set(provider { project.version.toString() })
    outputs.upToDateWhen { false }
}

tasks.named("check") {
    dependsOn(centralBundleZip)
}
