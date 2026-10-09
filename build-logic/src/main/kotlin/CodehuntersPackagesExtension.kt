import org.gradle.api.provider.Property

/** Package options a module sets in its build script through `codehuntersPackages { }`. */
abstract class CodehuntersPackagesExtension {

    /**
     * The package every class of this module lives under, e.g. `io.codehunters.messaging.jdbc`.
     * No other module may put classes under it, so a package never ends up split across jars.
     */
    abstract val root: Property<String>
}
