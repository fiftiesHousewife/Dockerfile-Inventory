// Phase 1b: read Artifactory over REST, rebuild the image family tree, link images to Dockerfiles.
plugins {
    id("inventory.java")
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(project(":http"))
}

// Tests read the cross-module contracts in testdata/; a changed contract must rerun them.
tasks.test {
    inputs.dir("../testdata").withPropertyName("contracts")
}
