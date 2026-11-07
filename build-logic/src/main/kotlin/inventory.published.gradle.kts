// A module published to Maven Central: jar, sources and Javadoc jars, a complete POM, signatures when a key is in
// the environment. `check` writes the bundle to build/central (unsigned without a key) and verifies it, so what a
// release uploads is tested on every build; scripts/release-parser.sh uploads it.

plugins {
    `java-library`
    `maven-publish`
    signing
}

java {
    withSourcesJar()
    withJavadocJar()
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
    }
}

tasks.named("check") {
    dependsOn(verifyBundle)
}
