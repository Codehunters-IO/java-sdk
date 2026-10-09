import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PackageLayoutTest {

    @TempDir
    lateinit var sources: File

    @Test
    fun should_report_nothing_when_packages_follow_the_convention() {
        javaFile("io/codehunters/audit/Auditable.java", "package io.codehunters.audit;")
        packageInfo("io/codehunters/audit")
        javaFile("io/codehunters/audit/config/AuditAutoConfiguration.java", "package io.codehunters.audit.config;")
        packageInfo("io/codehunters/audit/config")

        assertEquals(emptyList<String>(), PackageLayout.violations(ROOT, sources, emptyList()))
    }

    @Test
    fun should_report_a_layer_name_when_a_package_segment_is_hexagonal() {
        javaFile("io/codehunters/audit/domain/model/Event.java", "package io.codehunters.audit.domain.model;")
        packageInfo("io/codehunters/audit/domain/model")

        val violations = PackageLayout.violations(ROOT, sources, emptyList())

        assertTrue(violations.single().contains("io.codehunters.audit.domain.model"), violations.toString())
        assertTrue(violations.single().contains("domain"), violations.toString())
    }

    @Test
    fun should_report_a_missing_package_info_when_a_package_has_none() {
        javaFile("io/codehunters/audit/Auditable.java", "package io.codehunters.audit;")

        val violations = PackageLayout.violations(ROOT, sources, emptyList())

        assertTrue(violations.single().contains("package-info.java"), violations.toString())
    }

    @Test
    fun should_report_a_package_outside_the_root_when_module_declares_another_root() {
        javaFile("io/codehunters/tracing/TraceParent.java", "package io.codehunters.tracing;")
        packageInfo("io/codehunters/tracing")

        val violations = PackageLayout.violations(ROOT, sources, emptyList())

        assertTrue(violations.single().contains("outside io.codehunters.audit"), violations.toString())
    }

    @Test
    fun should_report_a_split_package_when_another_module_owns_a_more_specific_root() {
        javaFile("io/codehunters/messaging/jdbc/Dialect.java", "package io.codehunters.messaging.jdbc;")
        packageInfo("io/codehunters/messaging/jdbc")

        val violations =
            PackageLayout.violations("io.codehunters.messaging", sources, listOf("io.codehunters.messaging.jdbc"))

        assertTrue(violations.single().contains("io.codehunters.messaging.jdbc"), violations.toString())
        assertTrue(violations.single().contains("another module"), violations.toString())
    }

    @Test
    fun should_report_an_api_class_when_it_imports_configuration() {
        javaFile(
            "io/codehunters/audit/Auditable.java",
            "package io.codehunters.audit;\n\nimport io.codehunters.audit.config.AuditProperties;\n",
        )
        packageInfo("io/codehunters/audit")

        val violations = PackageLayout.violations(ROOT, sources, emptyList())

        assertTrue(violations.single().contains("Auditable.java"), violations.toString())
        assertTrue(violations.single().contains("config"), violations.toString())
    }

    private fun javaFile(path: String, content: String) {
        File(sources, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun packageInfo(dir: String) {
        javaFile("$dir/package-info.java", "package ${dir.replace('/', '.')};")
    }

    private companion object {
        const val ROOT = "io.codehunters.audit"
    }
}
