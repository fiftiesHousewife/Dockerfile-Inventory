# Building from source

## Requirements

- JDK 25, the newest long-term-support release. Gradle downloads it if it is not installed.
- Network access for `./gradlew check`: it fails when any dependency, plugin, Gradle or the JDK is behind its
  newest stable release. Offline, run `./gradlew check -x verifyUpToDate`.
- Behind a firewall that blocks Maven Central and the Gradle plugin portal: set `INVENTORY_MIRROR` to one Maven
  repository that serves both (an Artifactory remote or virtual repository), with `INVENTORY_MIRROR_USER` and
  `INVENTORY_MIRROR_PASSWORD` if it needs them. Where the JDK cannot be downloaded, install JDK 25 and add
  `org.gradle.java.installations.auto-download=false` to `~/.gradle/gradle.properties`; the up-to-date check also asks
  services.gradle.org and Adoptium, so skip it there (`-x verifyUpToDate`).
- For discovery: a GitHub Enterprise token that can read every organisation's repositories, and optionally the
  release index exported from the release database.

## Build

```bash
./gradlew check
```
