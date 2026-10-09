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

    @Test
    fun should_report_a_shared_root_when_another_module_declares_the_same_root() {
        javaFile("io/codehunters/audit/Auditable.java", "package io.codehunters.audit;")
        packageInfo("io/codehunters/audit")

        val violations = PackageLayout.violations(ROOT, sources, listOf(ROOT))

        assertTrue(violations.single().contains("another module also declares"), violations.toString())
    }

    @Test
    fun should_report_an_api_class_when_it_imports_configuration_statically() {
        javaFile(
            "io/codehunters/audit/Auditable.java",
            "package io.codehunters.audit;\n\nimport static io.codehunters.audit.config.AuditProperties.PREFIX;\n",
        )
        packageInfo("io/codehunters/audit")

        val violations = PackageLayout.violations(ROOT, sources, emptyList())

        assertTrue(violations.single().contains("Auditable.java"), violations.toString())
    }

    @Test
    fun should_report_an_api_class_when_it_names_configuration_by_qualified_name() {
        javaFile(
            "io/codehunters/audit/Auditable.java",
            "package io.codehunters.audit;\n\nclass Auditable { io.codehunters.audit.config.AuditProperties p; }\n",
        )
        packageInfo("io/codehunters/audit")

        val violations = PackageLayout.violations(ROOT, sources, emptyList())

        assertTrue(violations.single().contains("Auditable.java"), violations.toString())
    }

    @Test
    fun should_ignore_configuration_names_when_they_only_appear_in_comments() {
        javaFile(
            "io/codehunters/audit/Auditable.java",
            "package io.codehunters.audit;\n\n/** See {@link io.codehunters.audit.config.AuditProperties}. */\n" +
                "// io.codehunters.audit.config.AuditProperties\nclass Auditable {}\n",
        )
        packageInfo("io/codehunters/audit")

        assertEquals(emptyList<String>(), PackageLayout.violations(ROOT, sources, emptyList()))
    }

    @Test
    fun should_report_an_internal_package_when_its_package_info_does_not_say_it_is_not_api() {
        javaFile("io/codehunters/audit/internal/Helper.java", "package io.codehunters.audit.internal;")
        packageInfo("io/codehunters/audit/internal")

        val violations = PackageLayout.violations(ROOT, sources, emptyList())

        assertTrue(violations.single().contains("io.codehunters.audit.internal"), violations.toString())
        assertTrue(violations.single().contains("Not API"), violations.toString())
    }

    @Test
    fun should_accept_an_internal_package_when_its_package_info_says_it_is_not_api() {
        javaFile("io/codehunters/audit/internal/aspect/Helper.java", "package io.codehunters.audit.internal.aspect;")
        javaFile(
            "io/codehunters/audit/internal/aspect/package-info.java",
            "/** Not API; may change in any release. */\npackage io.codehunters.audit.internal.aspect;",
        )

        assertEquals(emptyList<String>(), PackageLayout.violations(ROOT, sources, emptyList()))
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
