// Phase 1b: read Artifactory over REST, rebuild the image family tree, link images to Dockerfiles.
plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

application {
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.lineage.LineageMain")
}
