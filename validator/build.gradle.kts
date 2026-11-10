// Phase 3c: each image judged through a platform profile (OpenShift first), its findings turned into actions.
plugins {
    id("inventory.java")
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

