// Phase 1: parse each unique Dockerfile blob with rewrite-docker and reduce it to facts and normal forms.
plugins {
    id("inventory.java")
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(platform(libs.rewrite.bom))
    implementation(libs.rewrite.core)
    implementation(project(":parser"))
    implementation(project(":reader"))
    implementation(project(":secrets"))
    compileOnly(libs.jetbrains.annotations)
    testCompileOnly(libs.jetbrains.annotations)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.yaml)
}

// Tests read the cross-module contracts in testdata/; a changed contract must rerun them.
tasks.test {
    inputs.dir("../testdata").withPropertyName("contracts")
}
