// A fork of OpenRewrite's rewrite-docker 8.90.4 (Apache 2.0), published as io.github.fiftieshousewife:dockerfile-parser.
plugins {
    `java-library`
    id("inventory.vendored")
    id("inventory.published")
}

group = "io.github.fiftieshousewife"
version = providers.gradleProperty("inventory.parserVersion").get()
base.archivesName.set("dockerfile-parser")

publishing.publications.named<MavenPublication>("library") {
    artifactId = "dockerfile-parser"
    pom {
        name.set("Dockerfile Parser")
        description.set("A lossless Dockerfile parser and tree: a fork of OpenRewrite's rewrite-docker 8.90.4 " +
            "with its parser bugs fixed.")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Dockerfile Parser",
            // rewrite-core declares no module name, so the parser cannot be a named module that requires it.
            "Automatic-Module-Name" to "io.github.fiftieshousewife.dockerfile.parser",
            "Implementation-Version" to version,
            "Based-On" to "org.openrewrite:rewrite-docker:8.90.4@398a6349648ca802aa050bdb6fe45a99395dce47",
        )
    }
}

val antlrGeneration by configurations.creating

dependencies {
    api(platform(libs.rewrite.bom))
    api(libs.rewrite.core)
    implementation(libs.antlr.runtime)
    compileOnlyApi(libs.jetbrains.annotations)
    antlrGeneration(libs.antlr.tool)

    testImplementation(platform(libs.rewrite.bom))
    testImplementation(libs.rewrite.test)
    testCompileOnly(libs.jetbrains.annotations)
}

// The generated lexer and parser are kept in the repository, as upstream keeps them; regenerate after a grammar change.
tasks.register<JavaExec>("generateAntlrSources") {
    group = "build"
    description = "Regenerates the ANTLR lexer and parser from src/main/antlr."
    mainClass.set("org.antlr.v4.Tool")
    classpath = antlrGeneration
    workingDir = projectDir
    args = listOf(
        "-o", "src/main/java/io/github/fiftieshousewife/dockerfile/parser/internal/grammar",
        "-package", "io.github.fiftieshousewife.dockerfile.parser.internal.grammar",
        "-Xexact-output-dir", "-visitor",
    ) + fileTree("src/main/antlr").matching { include("**/*.g4") }.map { it.relativeTo(projectDir).path }
}
