import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ReleaseTagTaskTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun writeBuild() {
        write("settings.gradle.kts", """include("sdk-foo", "sdk-bar", "sdk-bom")""")
        write("gradle.properties", ROOT_PROPERTIES)
        write("sdk-foo/build.gradle.kts", module("2.1.0"))
        write("sdk-bar/build.gradle.kts", module("0.3.0-SNAPSHOT"))
        write("sdk-bom/build.gradle.kts", module("1.0.0"))
    }

    @Test
    fun should_list_one_tag_per_released_module_and_skip_snapshots() {
        val output = runner().build().output

        assertEquals(listOf("bom/v1.0.0", "sdk-foo/v2.1.0"), output.trim().lines().sorted())
    }

    private fun runner(): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("releaseTag", "--quiet")

    private fun write(path: String, content: String) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun module(version: String) =
        """
        plugins {
            `java-library`
            id("codehunters.publishing")
        }

        version = "$version"
        """.trimIndent()

    private companion object {
        val ROOT_PROPERTIES =
            """
            repository.url=https://example.invalid/java-sdk
            github_packages_url=https://example.invalid/maven
            """.trimIndent()
    }
}
