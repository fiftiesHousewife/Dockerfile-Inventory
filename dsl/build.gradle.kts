// The Dockerfile model, typed builders that enforce policy, and the one renderer; no OpenRewrite. Published as
// io.github.fiftieshousewife:dockerfile-model.
plugins {
    `java-library`
    id("inventory.java")
    id("inventory.published")
}

group = "io.github.fiftieshousewife"
version = providers.gradleProperty("inventory.modelVersion").get()
base.archivesName.set("dockerfile-model")

publishing.publications.named<MavenPublication>("library") {
    artifactId = "dockerfile-model"
    pom {
        name.set("Dockerfile Model")
        description.set("An immutable Dockerfile model, a fluent builder that enforces a base-image policy, " +
            "and a renderer that writes the Dockerfile.")
    }
}
