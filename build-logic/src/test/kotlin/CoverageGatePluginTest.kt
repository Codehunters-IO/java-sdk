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
    fun should_pass_when_no_module_overrides_the_coverage_gates() {
        writeLibrary()
        write("gradle.properties", ROOT_PROPERTIES)

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyCoverageGates")?.outcome)
    }

    private fun runner(): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("verifyCoverageGates", "--stacktrace")

    private fun writeLibrary() {
        write("settings.gradle.kts", """rootProject.name = "lib"""")
        write("gradle/libs.versions.toml", TestCatalog.CONTENT)
        write(
            "build.gradle.kts",
            """
            plugins {
                id("codehunters.java-library")
            }

            version = "1.0.0"
            """.trimIndent(),
        )
    }

    private fun write(path: String, content: String) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private companion object {
        val ROOT_PROPERTIES =
            """
            repository.url=https://example.invalid/java-sdk
            github_packages_url=https://example.invalid/maven
            """.trimIndent()
    }
}
