// For code copied from another project (the parser fork): every gate of inventory.java except those its upstream
// style cannot meet unchanged (-Werror, SpotBugs); tests, coverage and the toolchain stay. Generated code (a
// `grammar` package) does not count towards coverage.

plugins {
    id("inventory.java")
}

val libs = versionCatalogs.named("libs")
fun lib(alias: String) = libs.findLibrary(alias).orElseThrow { IllegalStateException("missing library: $alias") }

dependencies {
    "compileOnly"(lib("lombok"))
    "annotationProcessor"(lib("lombok"))
    "testCompileOnly"(lib("lombok"))
    "testAnnotationProcessor"(lib("lombok"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.removeAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

tasks.matching { it.name.startsWith("spotbugs") }.configureEach {
    enabled = false
}

val generated = listOf("**/grammar/**")
tasks.withType<JacocoReport>().configureEach {
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude(generated) } })
}
tasks.withType<JacocoCoverageVerification>().configureEach {
    classDirectories.setFrom(classDirectories.files.map { fileTree(it) { exclude(generated) } })
}
