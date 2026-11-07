// A fork of OpenRewrite's rewrite-docker 8.90.4 (Apache 2.0), published as io.github.fiftieshousewife:dockerfile-parser.
plugins {
    `java-library`
    id("inventory.vendored")
}

val antlrGeneration by configurations.creating

dependencies {
    api(platform(libs.rewrite.bom))
    api(libs.rewrite.core)
    implementation(libs.antlr.runtime)
    compileOnly(libs.jetbrains.annotations)
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
