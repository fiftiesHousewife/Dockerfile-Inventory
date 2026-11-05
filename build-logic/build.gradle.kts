plugins {
    `kotlin-dsl`
}

// build-logic's own script has no typed `libs` accessor; read the catalogue programmatically.
val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).orElseThrow { IllegalStateException("missing library: $alias") }

dependencies {
    implementation(lib("spotbugs-gradle-plugin"))
    implementation(lib("gradle-versions-plugin"))

    testImplementation(lib("junit-jupiter"))
    testImplementation(lib("assertj-core"))
    testRuntimeOnly(lib("junit-platform-launcher"))
}

tasks.test {
    useJUnitPlatform()
}
