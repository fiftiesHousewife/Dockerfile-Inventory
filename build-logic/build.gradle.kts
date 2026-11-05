plugins {
    `kotlin-dsl`
}

// build-logic's own script has no typed `libs` accessor; read the catalogue programmatically.
val libs = the<VersionCatalogsExtension>().named("libs")
fun ver(alias: String) = libs.findVersion(alias).orElseThrow { IllegalStateException("missing version: $alias") }.requiredVersion

dependencies {
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:${ver("spotbugs-plugin")}")
}
