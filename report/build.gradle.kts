// Phase 2: collapse the estate into archetypes, rank them, catalogue bases, and write tables and a dashboard.
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
