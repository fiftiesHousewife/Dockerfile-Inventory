// Phase 1b: read Artifactory over REST, rebuild the image family tree, link images to Dockerfiles.
plugins {
    id("inventory.java")
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}
