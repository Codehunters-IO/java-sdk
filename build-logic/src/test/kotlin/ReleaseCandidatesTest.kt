import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.gradle.testkit.runner.GradleRunner

class ReleaseCandidatesTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun writeBuild() {
        write(
            "settings.gradle.kts",
            """
            include("sdk-foo", "sdk-bar", "sdk-bom")
            project(":sdk-foo").projectDir = file("group/sdk-foo")
            """.trimIndent()
        )
        write("gradle.properties", rootProperties())
        write("group/sdk-foo/build.gradle.kts", module("2.1.0"))
        write("sdk-bar/build.gradle.kts", module("0.3.0-SNAPSHOT"))
        write("sdk-bom/build.gradle.kts", BOM_SCRIPT)
    }

    @Test
    fun should_list_tag_gradle_path_and_directory_when_module_has_a_release_version() {
        runner("releaseCandidate").build()

        assertEquals("sdk-foo/v2.1.0 :sdk-foo group/sdk-foo\n", read("build/release-candidates/sdk-foo.txt"))
    }

    @Test
    fun should_tag_the_bom_as_bom_when_listing_it() {
        runner("releaseCandidate").build()

        assertEquals("bom/v1.0.0 :sdk-bom sdk-bom\n", read("build/release-candidates/bom.txt"))
    }

    @Test
    fun should_not_list_a_module_when_its_version_is_a_snapshot() {
        runner("releaseCandidate").build()

        assertFalse(File(projectDir, "build/release-candidates/sdk-bar.txt").exists())
    }

    @Test
    fun should_skip_the_upload_when_the_version_is_already_in_the_repository() {
        write("repo/io/codehunters/sdk-foo/2.1.0/sdk-foo-2.1.0.pom", "<project/>")

        runner(":sdk-foo:publish").build()

        assertEquals("<project/>", read("repo/io/codehunters/sdk-foo/2.1.0/sdk-foo-2.1.0.pom"))
    }

    private fun runner(task: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(task, "--stacktrace")

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
