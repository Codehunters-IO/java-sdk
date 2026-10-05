import org.gradle.api.provider.ListProperty

/** Publishing options a module sets in its build script through `codehuntersPublishing { }`. */
abstract class CodehuntersPublishingExtension {

    /** Artifact names, in the same group, that this module supersedes. */
    abstract val replacedArtifacts: ListProperty<String>

    /**
     * Declares that this module replaces [artifact], so a consumer that resolves both fails
     * with a capability conflict instead of loading the same classes twice.
     */
    fun replaces(artifact: String) {
        replacedArtifacts.add(artifact)
    }
}
