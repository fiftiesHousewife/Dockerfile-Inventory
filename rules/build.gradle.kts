// Phase 3: quality rules, detect-only first, over the facts and the parsed Dockerfiles; effective rules over lineage.
plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(project(":facts"))
    implementation(platform(libs.rewrite.bom))
    implementation(libs.rewrite.core)
    implementation(libs.rewrite.docker)
    compileOnly(libs.jetbrains.annotations)
    testCompileOnly(libs.jetbrains.annotations)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

application {
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.rules.RulesMain")
}
