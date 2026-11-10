// The docker-inventory command: every stage, and the survey that runs them in order.
plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(libs.slf4j.api)
    runtimeOnly(libs.log4j.slf4j2.impl)
    runtimeOnly(libs.log4j.core)
    runtimeOnly(libs.log4j.layout.template.json)
    testImplementation(libs.log4j.core)
    testImplementation(platform(libs.jackson.bom))
    testImplementation(libs.jackson.databind)
    testRuntimeOnly(libs.log4j.layout.template.json)
    implementation(project(":survey"))
    implementation(project(":discovery"))
    implementation(project(":facts"))
    implementation(project(":lineage"))
    implementation(project(":report"))
    implementation(project(":rules"))
    implementation(project(":validator"))
    implementation(project(":converter"))
}

version = providers.gradleProperty("inventory.cliVersion").get()

application {
    applicationName = "docker-inventory"
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.cli.DockerInventory")
}

tasks.processResources {
    val cliVersion = version.toString()
    inputs.property("version", cliVersion)
    filesMatching("docker-inventory.properties") {
        expand("version" to cliVersion)
    }
}

// The installed distribution is tested as a user runs it: its launcher, its classpath, its exit statuses.
tasks.named<Test>("test") {
    val installed = tasks.named<Sync>("installDist")
    dependsOn(installed)
    val distribution = installed.map { it.destinationDir.absolutePath }
    inputs.dir(distribution)
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Ddistribution=" + distribution.get()) })
}
