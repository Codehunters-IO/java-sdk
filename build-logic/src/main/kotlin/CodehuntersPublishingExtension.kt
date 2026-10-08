import org.gradle.api.provider.ListProperty

/** Publishing options a module sets in its build script through `codehuntersPublishing { }`. */
abstract class CodehuntersPublishingExtension {

    /** Artifact names, in the same group, that this module supersedes. */
    abstract val replacedArtifacts: ListProperty<String>

    /**
     * Declares that this module replaces each of [artifacts], so a consumer that resolves this
     * module and any of them fails with a capability conflict instead of loading the same classes
     * twice.
     */
    fun replaces(vararg artifacts: String) {
        replacedArtifacts.addAll(*artifacts)
    }
}
