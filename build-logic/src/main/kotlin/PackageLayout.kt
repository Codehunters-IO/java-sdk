import java.io.File

/**
 * The package layout every SDK library follows (ADR 0020): capability packages instead of
 * architectural layers, a `package-info.java` everywhere, one owning module per package, and an API
 * package that does not reach into configuration.
 */
object PackageLayout {

    /** Segments that name an architectural layer rather than a capability. */
    val LAYER_SEGMENTS =
        setOf(
            "application", "domain", "infrastructure", "usecase", "port", "ports",
            "drivenadapters", "entrypoint", "adapter", "adapters",
        )

    /**
     * @param root the package this module owns
     * @param sources the module's `src/main/java`
     * @param otherRoots the roots other modules own
     * @return one message per violation, empty when the layout is right
     */
    fun violations(root: String, sources: File, otherRoots: Collection<String>): List<String> =
        packages(sources).flatMap { (name, dir) -> packageViolations(root, name, dir, otherRoots) }

    private fun packages(sources: File): Map<String, File> =
        sources.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") }
            .map { it.parentFile }
            .distinct()
            .associateBy { it.relativeTo(sources).path.replace(File.separatorChar, '.') }
            .toSortedMap()

    private fun packageViolations(root: String, name: String, dir: File, otherRoots: Collection<String>) =
        listOfNotNull(
            outsideRoot(root, name),
            ownedElsewhere(root, name, otherRoots),
            layerSegment(name),
            missingPackageInfo(name, dir),
        ) + configImports(root, name, dir)

    private fun outsideRoot(root: String, name: String) =
        if (isUnder(name, root)) null else "$name is outside $root, the root this module declares"

    private fun ownedElsewhere(root: String, name: String, otherRoots: Collection<String>) =
        otherRoots.filter { it != root && isUnder(it, root) && isUnder(name, it) }
            .maxByOrNull { it.length }
            ?.let { "$name is under $it, which another module owns: the package would be split across jars" }

    private fun layerSegment(name: String) =
        name.split('.').firstOrNull { it in LAYER_SEGMENTS }
            ?.let { "$name uses the layer name '$it'; name packages by capability" }

    private fun missingPackageInfo(name: String, dir: File) =
        if (File(dir, "package-info.java").isFile) null else "$name has no package-info.java"

    private fun configImports(root: String, name: String, dir: File): List<String> =
        if (name != root) {
            emptyList()
        } else {
            dir.listFiles { file -> file.isFile && file.name.endsWith(".java") }.orEmpty()
                .filter { file -> file.readLines().any { it.startsWith("import $root.") && ".config." in it } }
                .sortedBy { it.name }
                .map { "${it.name} in the API package $root imports from a config package" }
        }

    private fun isUnder(name: String, root: String) = name == root || name.startsWith("$root.")
}
