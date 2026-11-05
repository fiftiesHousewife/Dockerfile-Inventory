// Phase 0: enumerate repositories, list trees at the released commit, fetch Dockerfile blobs once per SHA.
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
