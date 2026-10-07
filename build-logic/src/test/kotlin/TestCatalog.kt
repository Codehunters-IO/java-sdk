/** Minimal version catalog that the `codehunters.java-library` convention plugin reads. */
object TestCatalog {
    val CONTENT =
        """
        [versions]
        spring-boot = "4.0.2"
        google-java-format = "1.28.0"

        [libraries]
        spring-boot-dependencies = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot" }
        """.trimIndent()
}
