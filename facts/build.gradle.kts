// Phase 1: parse each unique Dockerfile blob with rewrite-docker and reduce it to facts and normal forms.
plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(platform(libs.rewrite.bom))
    implementation(libs.rewrite.core)
    implementation(project(":parser"))
    implementation(project(":reader"))
    implementation(project(":secrets"))
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
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.facts.FactsMain")
}

// Tests read the cross-module contracts in testdata/; a changed contract must rerun them.
tasks.test {
    inputs.dir("../testdata").withPropertyName("contracts")
}
