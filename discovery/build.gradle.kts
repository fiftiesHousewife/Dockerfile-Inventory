plugins {
    id("inventory.java")
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(project(":http"))
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.named<Test>("test") {
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
}

tasks.named<Test>("githubLive") {
    systemProperty("org.slf4j.simpleLogger.showDateTime", "true")
}
