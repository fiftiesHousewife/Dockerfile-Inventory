// Change a gate here, or through the inventory.* keys in gradle.properties, never in a module.

plugins {
    java
    jacoco
    id("com.github.spotbugs")
}

val libs = versionCatalogs.named("libs")
fun lib(alias: String) = libs.findLibrary(alias).orElseThrow { IllegalStateException("missing library: $alias") }
fun ver(alias: String) =
    libs.findVersion(alias).orElseThrow { IllegalStateException("missing version: $alias") }.requiredVersion

val javaRelease = providers.gradleProperty("inventory.javaRelease").orElse("25").get().toInt()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaRelease))
    }
}

dependencies {
    "spotbugsSlf4j"(lib("slf4j-simple"))
    "compileOnly"(lib("jspecify"))
    "testImplementation"(lib("junit-jupiter"))
    "testImplementation"(lib("assertj-core"))
    "testRuntimeOnly"(lib("junit-platform-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Werror"))
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("estate", "images")
    }
    finalizedBy(tasks.named("jacocoTestReport"))
}

// The bulk checks: a real estate converted, real images built and compared. Outside check, as they need an
// estate on disk, Docker and the network; each skips with its reason when its input is missing.
mapOf("estateTest" to "estate", "imageTest" to "images").forEach { (name, tag) ->
    tasks.register<Test>(name) {
        description = "Runs the tests tagged $tag"
        group = "verification"
        val test = tasks.named<Test>("test").get()
        testClassesDirs = test.testClassesDirs
        classpath = test.classpath
        useJUnitPlatform {
            includeTags(tag)
        }
        outputs.upToDateWhen { false }
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
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

// A stage's `run` starts in the repository root, so a relative OUT_DIR or DISCOVERY_DIR means the same thing
// whichever module runs it (Gradle would otherwise start in the module's own folder).
val repositoryRoot = rootDir
plugins.withId("application") {
    tasks.named<JavaExec>("run") {
        workingDir = repositoryRoot
    }
}
