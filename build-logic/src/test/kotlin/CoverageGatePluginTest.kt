import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CoverageGatePluginTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun should_fail_when_module_lowers_the_branch_coverage_gate() {
        writeLibrary()
        write("gradle.properties", "$ROOT_PROPERTIES\ncoverage.branch.minimum=0.60")

        val output = runner().buildAndFail().output

        assertTrue(output.contains("coverage.branch.minimum"), output)
        assertTrue(output.contains("gates are fixed"), output)
    }

    @Test
    fun should_fail_when_a_subproject_lowers_the_gate_in_its_own_gradle_properties() {
        write("settings.gradle.kts", """include("lib")""")
        write("gradle.properties", ROOT_PROPERTIES)
        write("gradle/libs.versions.toml", TestCatalog.CONTENT)
        write("lib/build.gradle.kts", LIBRARY_SCRIPT)
        write("lib/gradle.properties", "coverage.branch.minimum=0.60")

        val output = runner(":lib:verifyCoverageGates").buildAndFail().output

        assertTrue(output.contains(":lib sets coverage.branch.minimum=0.60"), output)
    }

    @Test
    fun should_run_the_gate_check_as_part_of_check() {
        writeLibrary()
        write("gradle.properties", ROOT_PROPERTIES)

        val output = runner("check", "--dry-run").build().output

        assertTrue(output.contains(":verifyCoverageGates SKIPPED"), output)
    }

    @Test
    fun should_pass_when_no_module_overrides_the_coverage_gates() {
        writeLibrary()
        write("gradle.properties", ROOT_PROPERTIES)

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyCoverageGates")?.outcome)
    }

    private fun runner(vararg arguments: String = arrayOf("verifyCoverageGates")): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")

    private fun writeLibrary() {
        write("settings.gradle.kts", """rootProject.name = "lib"""")
        write("gradle/libs.versions.toml", TestCatalog.CONTENT)
        write("build.gradle.kts", LIBRARY_SCRIPT)
    }

    private fun write(path: String, content: String) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private companion object {
        val LIBRARY_SCRIPT =
            """
            plugins {
                id("codehunters.java-library")
            }

            version = "1.0.0"
            """.trimIndent()

        val ROOT_PROPERTIES =
            """
            repository.url=https://example.invalid/java-sdk
            github_packages_url=https://example.invalid/maven
            """.trimIndent()
    }
}
