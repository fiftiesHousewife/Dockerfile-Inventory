// The contributors' tools: fixtures and test inputs drawn from a run. Not part of the pipeline, nor of the image.
plugins {
    id("inventory.java")
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}

// ./gradlew collectCorpus --args=out/all: the Dockerfiles a run found hard, into the fixture bank.
tasks.register<JavaExec>("collectCorpus") {
    group = "tools"
    description = "Collects the Dockerfiles a run found hard into testdata/corpus/dockerfiles"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.tools.CorpusCollect")
    workingDir = rootDir
}
