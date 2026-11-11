// A library published to Maven Central: its jar, sources and Javadoc jars and Gradle module metadata, through
// inventory.central; the jar carries a licence and a module name.

import java.util.zip.ZipFile

plugins {
    `java-library`
    id("inventory.central")
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

// Javadoc that is wrong (a broken link, bad HTML) fails the build; a missing @param or @return does not, since a
// record's component names say what its parameters are.
tasks.withType<Javadoc>().configureEach {
    val doclet = options as StandardJavadocDocletOptions
    doclet.addStringOption("Xdoclint:all,-missing", "-quiet")
    doclet.addBooleanOption("Werror", true)
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            versionMapping {
                allVariants {
                    fromResolutionResult()
                }
            }
        }
    }
}

tasks.named("verifyBundle") {
    val jar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    doLast {
        ZipFile(jar.get().asFile).use { file ->
            listOf("META-INF/LICENSE", "META-INF/NOTICE").forEach {
                check(file.getEntry(it) != null) { "the jar lacks $it" }
            }
            val manifest = file.getInputStream(file.getEntry("META-INF/MANIFEST.MF")).reader().readText()
            check(file.getEntry("module-info.class") != null || manifest.contains("Automatic-Module-Name:")) {
                "the jar is neither a module nor names one (Automatic-Module-Name)"
            }
        }
    }
}
