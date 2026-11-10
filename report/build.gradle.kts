// Phase 2: rank the archetypes, write the catalogue, and the HTML pages drawn from it.
plugins {
    id("inventory.java")
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(project(":secrets"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

