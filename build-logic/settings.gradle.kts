// INVENTORY_MIRROR: one Maven repository (an Artifactory remote or virtual repository, say) used in place of Maven
// Central and the Gradle plugin portal, for networks that block them; credentials, if any, from the environment.
val mirror = providers.environmentVariable("INVENTORY_MIRROR").orNull
dependencyResolutionManagement {
    repositories {
        if (mirror != null) {
            maven {
                url = uri(mirror)
                providers.environmentVariable("INVENTORY_MIRROR_USER").orNull?.let { user ->
                    credentials {
                        username = user
                        password = providers.environmentVariable("INVENTORY_MIRROR_PASSWORD").orNull
                    }
                }
            }
        } else {
            gradlePluginPortal()
            mavenCentral()
        }
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
