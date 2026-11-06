// Phase 5: a parsed Dockerfile -> the Phase 4 model -> a generated builder class, gated by a round trip.
plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(project(":facts"))
    implementation(project(":dsl"))
    implementation(platform(libs.rewrite.bom))
    implementation(libs.rewrite.core)
    implementation(libs.rewrite.docker)
    compileOnly(libs.jetbrains.annotations)
    testCompileOnly(libs.jetbrains.annotations)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.javapoet)
}

application {
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.converter.ConverterMain")
}

// The round-trip test reads the cross-module contracts in testdata/.
tasks.test {
    inputs.dir("../testdata").withPropertyName("contracts")
}

// The round-trip test converts the real Dockerfile kept as a facts fixture.
tasks.test {
    inputs.dir("../facts/src/test/resources/fixtures/dockerfiles").withPropertyName("dockerfiles")
}
