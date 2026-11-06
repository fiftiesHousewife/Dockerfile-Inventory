// Phase 1: parse each unique Dockerfile blob with rewrite-docker and reduce it to facts and normal forms.
plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(platform(libs.rewrite.bom))
    implementation(libs.rewrite.core)
    implementation(libs.rewrite.docker)
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
