// The contributors' tools: fixtures and test inputs drawn from a run. Not part of the pipeline, nor of the image.
plugins {
    id("inventory.java")
}

dependencies {
    implementation(project(":http"))
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

// ./gradlew collectImages --args=out/okd: the images a lineage run read, into the fixture bank.
tasks.register<JavaExec>("collectImages") {
    group = "tools"
    description = "Collects a lineage run's images into testdata/corpus/images"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.tools.ImageCollect")
    workingDir = rootDir
}

// ./gradlew selectOkd --args="5.0.0-okd-scos.5 out/okd": the OKD images a lineage run copies, from quay.io.
tasks.register<JavaExec>("selectOkd") {
    group = "tools"
    description = "Picks one OKD release's images for a lineage run, from quay.io"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.tools.OkdSelect")
    workingDir = rootDir
}
