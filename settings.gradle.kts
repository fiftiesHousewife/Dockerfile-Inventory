// Convention plugins live in build-logic/; including it makes `inventory.java` available to every module.
pluginManagement {
    includeBuild("build-logic")
    // The toolchain resolver lets Gradle download the JDK in inventory.javaRelease. Its version lives in the
    // catalogue (where the up-to-date check sees it); a settings plugins block cannot read the catalogue directly.
    val foojay = file("gradle/libs.versions.toml").readLines()
        .first { it.startsWith("foojay-resolver =") }.substringAfter('"').substringBefore('"')
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojay
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Only for the root's up-to-date check of build-logic's plugins, some of which are published nowhere else.
        gradlePluginPortal {
            content {
                includeGroup("io.github.ben-manes")
                includeGroup("com.github.spotbugs.snom")
                includeGroup("org.gradle.toolchains")
            }
        }
    }
}

rootProject.name = "dockerfile-inventory"

// One module per pipeline stage, added when its phase starts (see BACKLOG.md).
include("discovery")
include("facts")
include("lineage")
include("report")
include("rules")
include("dsl")
include("converter")
