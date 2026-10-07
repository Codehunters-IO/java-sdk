import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.gradle.testkit.runner.GradleRunner

class ReleasedTagsTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun writeBuild() {
        write("settings.gradle.kts", """include("sdk-foo", "sdk-bar", "sdk-bom")""")
        write("gradle.properties", rootProperties())
        write("sdk-foo/build.gradle.kts", module("2.1.0"))
        write("sdk-bar/build.gradle.kts", module("0.3.0-SNAPSHOT"))
        write("sdk-bom/build.gradle.kts", BOM_SCRIPT)
    }

    @Test
    fun should_record_one_tag_per_module_uploaded_in_this_build() {
        runner().build()

        assertEquals("sdk-foo/v2.1.0\n", read("build/released-tags/sdk-foo.txt"))
        assertEquals("bom/v1.0.0\n", read("build/released-tags/bom.txt"))
    }

    @Test
    fun should_not_record_a_snapshot_module() {
        runner().build()

        assertFalse(File(projectDir, "build/released-tags/sdk-bar.txt").exists())
    }

    @Test
    fun should_not_record_a_module_already_in_the_repository() {
        write("repo/io/codehunters/sdk-foo/2.1.0/sdk-foo-2.1.0.pom", "<project/>")

        runner().build()

        assertFalse(File(projectDir, "build/released-tags/sdk-foo.txt").exists())
    }

    private fun runner(): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("publish", "--stacktrace")

    private fun write(path: String, content: String) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun read(path: String) = File(projectDir, path).readText()

    private fun rootProperties() =
        """
        repository.url=https://example.invalid/java-sdk
        github_packages_url=${File(projectDir, "repo").toURI()}
        """.trimIndent()

    private fun module(version: String) =
        """
        plugins {
            `java-library`
            id("codehunters.publishing")
        }

        version = "$version"

        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                }
            }
        }
        """.trimIndent()

    private companion object {
        val BOM_SCRIPT =
            """
            plugins {
                id("codehunters.bom")
            }

            version = "1.0.0"

            dependencies {
                constraints {
                    api(project(":sdk-foo"))
                }
            }
            """.trimIndent()
    }
}
