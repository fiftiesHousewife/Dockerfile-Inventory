// Phase 3c: each image judged through a platform profile (OpenShift first), its findings turned into actions.
plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

application {
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.validator.ValidatorMain")
}
