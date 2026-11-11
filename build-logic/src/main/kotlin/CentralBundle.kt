import java.io.File
import java.util.zip.ZipFile

// What the Central Portal checks in a bundle before it takes it, checked here first so `check` fails instead.
object CentralBundle {

    fun missing(dir: File, stem: String, extension: String): List<String> {
        val required = listOf(".$extension", "-sources.jar", "-javadoc.jar", ".pom").map { stem + it }
        return required.filterNot { dir.resolve(it).isFile } +
            required.flatMap { listOf("$it.md5", "$it.sha1") }.filterNot { dir.resolve(it).isFile }
    }

    fun pomGaps(pom: String) =
        listOf("<name>", "<description>", "<url>", "<licenses>", "<developers>", "<scm>").filterNot { pom.contains(it) }

    fun distributionGaps(zip: File, stem: String, launcher: String): List<String> = ZipFile(zip).use { file ->
        val names = file.entries().asSequence().map { it.name }.toSet()
        listOfNotNull(
            "$stem/bin/$launcher".takeUnless { it in names },
            "a jar in $stem/lib/".takeUnless { names.any { it.startsWith("$stem/lib/") && it.endsWith(".jar") } },
            "$stem/LICENSE".takeUnless { it in names },
            "$stem/NOTICE".takeUnless { it in names })
    }
}
