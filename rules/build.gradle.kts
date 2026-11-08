// Phase 3: quality rules, detect-only first, over the facts and the parsed Dockerfiles; effective rules over lineage.
plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(project(":facts"))
    implementation(project(":secrets"))
    implementation(platform(libs.rewrite.bom))
    implementation(libs.rewrite.core)
    implementation(project(":parser"))
    implementation(project(":reader"))
    // OpenRewrite's bundled JGit logs through SLF4J when it reports a file the parser cannot print back; without it
    // that report fails with NoClassDefFoundError and hides the parser's own message.
    runtimeOnly(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop)
    compileOnly(libs.jetbrains.annotations)
    testCompileOnly(libs.jetbrains.annotations)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

application {
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.rules.RulesMain")
}

// The Phase 4 acceptance test reads the dsl's golden files; a changed golden must rerun it.
tasks.test {
    inputs.dir("../dsl/src/test/resources/golden").withPropertyName("goldens")
}
