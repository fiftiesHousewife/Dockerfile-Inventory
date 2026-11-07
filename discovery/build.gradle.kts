plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

application {
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.discovery.DiscoveryMain")
}
