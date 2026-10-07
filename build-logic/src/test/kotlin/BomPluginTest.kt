import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BomPluginTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun writeBuild() {
        write("settings.gradle.kts", """include("lib-a", "lib-b", "snapshot-lib", "bom")""")
        write("gradle.properties", ROOT_PROPERTIES)
        write("gradle/libs.versions.toml", TestCatalog.CONTENT)
        write("lib-a/build.gradle.kts", library("1.0.0"))
        write("lib-b/build.gradle.kts", library("2.0.0"))
        write("snapshot-lib/build.gradle.kts", library("0.1.0-SNAPSHOT"))
    }

    @Test
    fun should_fail_when_a_released_module_is_missing_from_the_bom() {
        write("bom/build.gradle.kts", bom(":lib-a"))

        val output = runner().buildAndFail().output

        assertTrue(output.contains("missing from the BOM: [:lib-b]"), output)
    }

    @Test
    fun should_fail_when_the_bom_constrains_a_snapshot_module() {
        write("bom/build.gradle.kts", bom(":lib-a", ":lib-b", ":snapshot-lib"))

        val output = runner().buildAndFail().output

        assertTrue(output.contains("SNAPSHOT modules in the BOM: [:snapshot-lib]"), output)
    }

    @Test
    fun should_pass_when_the_bom_constrains_every_released_module() {
        write("bom/build.gradle.kts", bom(":lib-a", ":lib-b"))

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":bom:verifyBomConstraints")?.outcome)
    }

    @Test
    fun should_publish_the_platform_with_the_constrained_versions() {
        write("bom/build.gradle.kts", bom(":lib-a", ":lib-b"))

        runner(":bom:generatePomFileForMavenBomPublication").build()

        val pom = File(projectDir, "bom/build/publications/mavenBom/pom-default.xml").readText()
        val compact = pom.replace(Regex("\\s+"), "")
        assertTrue(compact.contains("<artifactId>lib-b</artifactId><version>2.0.0</version>"), pom)
        assertFalse(pom.contains("snapshot-lib"), pom)
    }

    private fun runner(task: String = ":bom:verifyBomConstraints"): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(task, "--stacktrace")

    private fun write(path: String, content: String) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun library(version: String) =
        """
        plugins {
            id("codehunters.java-library")
        }

        version = "$version"
        """.trimIndent()

    private fun bom(vararg modules: String) =
        """
        plugins {
            id("codehunters.bom")
        }

        version = "1.0.0"

        dependencies {
            constraints {
                ${modules.joinToString("\n") { "api(project(\"$it\"))" }}
            }
        }
        """.trimIndent()

    private companion object {
        val ROOT_PROPERTIES =
            """
            repository.url=https://example.invalid/java-sdk
            github_packages_url=https://example.invalid/maven
            """.trimIndent()
    }
}
