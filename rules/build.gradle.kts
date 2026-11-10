// Phase 3: quality rules, detect-only first, over the facts and the parsed Dockerfiles; effective rules over lineage.
plugins {
    id("inventory.java")
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(project(":facts"))
    implementation(project(":secrets"))
    implementation(platform(libs.rewrite.bom))
    implementation(libs.rewrite.core)
    implementation(project(":parser"))
    implementation(project(":reader"))
    compileOnly(libs.jetbrains.annotations)
    testCompileOnly(libs.jetbrains.annotations)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

// The Phase 4 acceptance test reads the dsl's golden files; a changed golden must rerun it.
tasks.test {
    inputs.dir("../dsl/src/test/resources/golden").withPropertyName("goldens")
}
