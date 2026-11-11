import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CentralBundleTest {

    @Test
    fun aBundleLackingItsZipAndAChecksumHasBothNamed(@TempDir dir: Path) {
        listOf("-sources.jar", "-javadoc.jar", ".pom").flatMap { listOf("t-1$it", "t-1$it.md5", "t-1$it.sha1") }
            .forEach { Files.writeString(dir.resolve(it), "x") }
        Files.delete(dir.resolve("t-1.pom.sha1"))
        assertThat(CentralBundle.missing(dir.toFile(), "t-1", "zip"))
            .containsExactly("t-1.zip", "t-1.zip.md5", "t-1.zip.sha1", "t-1.pom.sha1")
    }

    @Test
    fun aPomWithoutDevelopersOrScmHasBothNamed() {
        assertThat(CentralBundle.pomGaps("<name>n</name><description>d</description><url>u</url><licenses></licenses>"))
            .containsExactly("<developers>", "<scm>")
    }

    @Test
    fun aDistributionWithOnlyItsLauncherLacksJarsLicenceAndNotice(@TempDir dir: Path) {
        val zip = dir.resolve("d.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use {
            it.putNextEntry(ZipEntry("d-1/bin/d"))
            it.closeEntry()
        }
        assertThat(CentralBundle.distributionGaps(zip.toFile(), "d-1", "d"))
            .containsExactly("a jar in d-1/lib/", "d-1/LICENSE", "d-1/NOTICE")
    }
}
