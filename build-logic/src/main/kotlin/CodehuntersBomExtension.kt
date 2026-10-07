import org.gradle.api.provider.SetProperty

/** BOM options the platform module sets in its build script through `codehuntersBom { }`. */
abstract class CodehuntersBomExtension {

    /**
     * Paths of released libraries deliberately left out of the BOM, such as a module that is
     * leaving the SDK. Without this, a released library missing from the BOM fails the build.
     */
    abstract val excludedModules: SetProperty<String>
}
