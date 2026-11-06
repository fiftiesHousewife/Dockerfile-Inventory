package io.github.fiftieshousewife.dockerfileinventory.dsl;

/** The package managers the renderer knows how to install and clean with. */
public enum PackageManager {
    MICRODNF,
    DNF,
    YUM,
    APK,
    APT
}
