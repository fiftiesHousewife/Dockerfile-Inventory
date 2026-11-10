// One command for a survey of an organisation: every stage in order, or a try on a few repositories.
plugins {
    id("inventory.java")
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(project(":discovery"))
    implementation(project(":facts"))
    implementation(project(":lineage"))
    implementation(project(":report"))
    implementation(project(":rules"))
    implementation(project(":validator"))
    implementation(project(":converter"))
}

