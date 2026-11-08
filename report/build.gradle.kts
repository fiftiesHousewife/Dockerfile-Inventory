// Phase 2: rank the archetypes, write the catalogue, and the HTML pages drawn from it.
plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(project(":secrets"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

application {
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.report.ReportMain")
}
