plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(project(":http"))
}

application {
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.discovery.DiscoveryMain")
}
