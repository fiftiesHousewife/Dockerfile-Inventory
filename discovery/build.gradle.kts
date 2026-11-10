plugins {
    id("inventory.java")
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(project(":http"))
}

