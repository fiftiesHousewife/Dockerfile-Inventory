// Convention plugins live in build-logic/; including it makes `inventory.java` available to every module.
pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "dockerfile-inventory"

// One module per pipeline stage, added when its phase starts (see BACKLOG.md).
include("discovery")
