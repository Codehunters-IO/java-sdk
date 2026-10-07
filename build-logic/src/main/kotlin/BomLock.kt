import java.io.File

/**
 * The library versions a released BOM constrains, committed next to the BOM. A library released
 * under an unchanged BOM version would otherwise never reach consumers of that BOM.
 */
object BomLock {

    const val VERSION_KEY = "bom.version"

    /** Reads a lock file into key/value pairs, or null when the file doesn't exist. */
    fun read(file: File): Map<String, String>? =
        if (!file.exists()) {
            null
        } else {
            file.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
        }

    /** Renders the lock of [bomPath] at [bomVersion], one sorted `module=version` line each. */
    fun render(bomPath: String, bomVersion: String, modules: Map<String, String>): String =
        buildString {
            appendLine(
                "# Versions $bomPath was released with. " +
                    "Regenerate with ./gradlew $bomPath:updateBomLock after bumping it."
            )
            appendLine("$VERSION_KEY=$bomVersion")
            modules.toSortedMap().forEach { (name, version) -> appendLine("$name=$version") }
        }

    /** Describes each module whose version differs between [locked] and [current]. */
    fun changes(locked: Map<String, String>, current: Map<String, String>): List<String> =
        ((locked.keys - VERSION_KEY) + current.keys).sorted().mapNotNull { name ->
            val before = locked[name]
            val after = current[name]
            if (before == after) null else "$name ${before ?: "absent"} -> ${after ?: "absent"}"
        }
}
