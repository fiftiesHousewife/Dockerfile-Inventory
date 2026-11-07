package io.github.fiftieshousewife.dockerfileinventory.buildlogic

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UpToDateTest {

    @Test
    fun `lists each outdated dependency with its newest release`() {
        val report = """
            {"outdated": {"dependencies": [
              {"group": "org.junit.jupiter", "name": "junit-jupiter", "version": "6.1.2",
               "available": {"release": "6.1.3"}}
            ]}}
        """.trimIndent()

        assertThat(UpToDate.outdatedDependencies(report))
            .containsExactly("org.junit.jupiter:junit-jupiter 6.1.2 -> 6.1.3")
    }

    @Test
    fun `fails when the report says a dependency could not be resolved`() {
        val report = """
            {"outdated": {"dependencies": []},
             "unresolved": {"dependencies": [
               {"group": "io.github.ben-manes", "name": "gradle-versions-plugin", "version": "0.67.0"}]}}
        """.trimIndent()

        assertThat(UpToDate.outdatedDependencies(report))
            .containsExactly("io.github.ben-manes:gradle-versions-plugin 0.67.0 could not be checked")
    }

    @Test
    fun `reports nothing when every dependency is current`() {
        assertThat(UpToDate.outdatedDependencies("""{"outdated": {"dependencies": []}}""")).isEmpty()
    }

    @Test
    fun `flags an older Gradle`() {
        assertThat(UpToDate.gradleBehind("9.8.1", """{"version": "9.8.2"}""")).contains("9.8.1 -> 9.8.2")
    }

    @Test
    fun `accepts the current Gradle`() {
        assertThat(UpToDate.gradleBehind("9.8.2", """{"version": "9.8.2"}""")).isNull()
    }

    @Test
    fun `flags a JDK older than the newest LTS`() {
        assertThat(UpToDate.jdkBehind(21, """{"most_recent_lts": 25, "most_recent_feature_release": 27}"""))
            .contains("21 -> 25")
    }

    @Test
    fun `accepts the newest LTS even when a newer feature release exists`() {
        assertThat(UpToDate.jdkBehind(25, """{"most_recent_lts": 25, "most_recent_feature_release": 27}""")).isNull()
    }
}
