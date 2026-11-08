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
    implementation(project(":parser"))
    // OpenRewrite's bundled JGit logs through SLF4J when it reports a file the parser cannot print back; without it
    // that report fails with NoClassDefFoundError and hides the parser's own message.
    runtimeOnly(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop)
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

// scripts/compare-images.sh runs ImageCompareMain through this task, so it runs on the project's toolchain.
tasks.register<JavaExec>("compareImages") {
    group = "application"
    description = "Renders regenerated Dockerfiles (STEP=render) or compares built images (STEP=compare)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.converter.ImageCompareMain")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    workingDir = rootProject.projectDir
}
