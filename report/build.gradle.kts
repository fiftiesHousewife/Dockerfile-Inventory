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

val catalogueStartScripts by tasks.registering(CreateStartScripts::class) {
    applicationName = "catalogue"
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.report.CatalogueMain")
    outputDir = layout.buildDirectory.dir("catalogue-scripts").get().asFile
    classpath = tasks.startScripts.get().classpath
}

distributions.main {
    contents {
        from(catalogueStartScripts) {
            into("bin")
        }
    }
}
