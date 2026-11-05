// Shared shape for every module: Java toolchain, tests, coverage gate, SpotBugs.
// Change a gate here, or through the inventory.* keys in gradle.properties, never in a module.

plugins {
    java
    jacoco
    id("com.github.spotbugs")
}

val libs = versionCatalogs.named("libs")
fun lib(alias: String) = libs.findLibrary(alias).orElseThrow { IllegalStateException("missing library: $alias") }
fun ver(alias: String) = libs.findVersion(alias).orElseThrow { IllegalStateException("missing version: $alias") }.requiredVersion

val javaRelease = providers.gradleProperty("inventory.javaRelease").orElse("25").get().toInt()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaRelease))
    }
}

dependencies {
    "compileOnly"(lib("jspecify"))
    "testImplementation"(lib("junit-jupiter"))
    "testImplementation"(lib("assertj-core"))
    "testRuntimeOnly"(lib("junit-platform-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

jacoco {
    toolVersion = ver("jacoco")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

val minLineCoverage = providers.gradleProperty("inventory.minLineCoverage").map { it.toBigDecimal() }

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    if (minLineCoverage.isPresent) {
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = minLineCoverage.get()
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

spotbugs {
    toolVersion.set(ver("spotbugs"))
    ignoreFailures.set(false)
    effort.set(com.github.spotbugs.snom.Effort.DEFAULT)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
}
