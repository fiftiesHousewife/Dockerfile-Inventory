// Corporate networks: proxies, a corporate CA, retries, rate pacing, token files. Shared by discovery and lineage.
plugins {
    id("inventory.java")
}

dependencies {
    implementation(libs.failsafe)
}
