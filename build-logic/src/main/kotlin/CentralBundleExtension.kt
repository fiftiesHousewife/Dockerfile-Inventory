import org.gradle.api.provider.Property

// What a module's Central bundle holds as its main file: a library's jar, or the command's distribution zip.
abstract class CentralBundleExtension {
    abstract val packaging: Property<String>
}
