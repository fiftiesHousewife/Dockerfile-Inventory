// The root build holds no code. Every module applies the `inventory.java` convention plugin from build-logic/;
// the root applies the up-to-date check, which covers every module.
plugins {
    id("inventory.dependency-updates")
}

// build-logic is an included build, so its tests (the up-to-date check's logic) are not part of this build's check.
// Its :test, not its :check: plugin validation runs in a worker under ~/.gradle/workers, which the Claude Code
// sandbox cannot write to yet.
tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
}
