import java.io.File
import java.util.jar.JarOutputStream
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CodehuntersPublishingPluginTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun should_publish_replaced_artifact_capability() {
        write("settings.gradle.kts", """rootProject.name = "new-lib"""")
        write("gradle.properties", ROOT_PROPERTIES)
        write("build.gradle.kts", libraryScript())

        val output = runner("outgoingVariants").build().output

        assertTrue(output.contains("io.codehunters:legacy-lib:6.0.0"), output)
        assertTrue(output.contains("io.codehunters:new-lib:6.0.0"), output)
    }

    @Test
    fun should_fail_resolution_when_old_and_new_artifacts_are_both_requested() {
        writeLegacyArtifact()
        write("settings.gradle.kts", """include("lib", "app")""")
        write("gradle.properties", ROOT_PROPERTIES)
        write("lib/build.gradle.kts", libraryScript())
        write("app/build.gradle.kts", APP_SCRIPT)

        val output = runner(":app:resolveRuntimeClasspath").buildAndFail().output

        assertTrue(output.contains("Cannot select module with conflict on capability"), output)
        assertTrue(output.contains("io.codehunters:legacy-lib"), output)
    }

    private fun runner(task: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(task, "--stacktrace")

    private fun write(path: String, content: String) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun writeLegacyArtifact() {
        val dir = "repo/io/codehunters/legacy-lib/1.0.0"
        write("$dir/legacy-lib-1.0.0.pom", LEGACY_POM)
        JarOutputStream(File(projectDir, "$dir/legacy-lib-1.0.0.jar").outputStream()).close()
    }

    private fun libraryScript() =
        """
        plugins {
            `java-library`
            id("codehunters.publishing")
        }

        version = "6.0.0"

        codehuntersPublishing {
            replaces("legacy-lib")
        }
        """.trimIndent()

    private companion object {
        val ROOT_PROPERTIES =
            """
            repository.url=https://example.invalid/java-sdk
            github_packages_url=https://example.invalid/maven
            """.trimIndent()

        val APP_SCRIPT =
            """
            plugins {
                `java-library`
            }

            repositories {
                maven { url = uri(rootProject.file("repo")) }
            }

            dependencies {
                implementation(project(":lib"))
                implementation("io.codehunters:legacy-lib:1.0.0")
            }

            tasks.register("resolveRuntimeClasspath") {
                val classpath = configurations.runtimeClasspath
                doLast { classpath.get().files }
            }
            """.trimIndent()

        val LEGACY_POM =
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>io.codehunters</groupId>
              <artifactId>legacy-lib</artifactId>
              <version>1.0.0</version>
            </project>
            """.trimIndent()
    }
}
