// One command for a survey of an organisation: every stage in order, or a try on a few repositories.
plugins {
    id("inventory.java")
    application
}

dependencies {
    implementation(project(":discovery"))
    implementation(project(":facts"))
    implementation(project(":lineage"))
    implementation(project(":report"))
    implementation(project(":rules"))
    implementation(project(":validator"))
}

application {
    mainClass.set("io.github.fiftieshousewife.dockerfileinventory.survey.SurveyMain")
}

// From a checkout, `out/` is the repository's, as for every other stage.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
