// Dockerfile text to the model (dockerfile-model), through the parser (dockerfile-parser); the converter's reading
// half. Published as io.github.fiftieshousewife:dockerfile-reader.
plugins {
    `java-library`
    id("inventory.java")
    id("inventory.published")
}

group = "io.github.fiftieshousewife"
version = providers.gradleProperty("inventory.readerVersion").get()
base.archivesName.set("dockerfile-reader")

dependencies {
    api(project(":parser"))
    api(project(":dsl"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

publishing.publications.named<MavenPublication>("library") {
    artifactId = "dockerfile-reader"
    pom {
        name.set("Dockerfile Reader")
        description.set("Reads a Dockerfile into the dockerfile-model: every instruction typed, package installs " +
            "read from RUN, what cannot be typed kept as written.")
    }
}

tasks.jar {
    manifest {
        // rewrite-core, which the parser's tree extends, declares no module name, so neither can this be a named module.
        attributes("Automatic-Module-Name" to "io.github.fiftieshousewife.dockerfile.reader")
    }
}
