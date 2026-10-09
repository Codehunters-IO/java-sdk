import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NoLegacyAliasesPluginTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun should_fail_when_module_registers_an_environment_post_processor() {
        writeLibrary()
        write(FACTORIES, "$POST_PROCESSOR=io.codehunters.demo.config.AliasPostProcessor\n")

        val output = runner().buildAndFail().output

        assertTrue(output.contains("EnvironmentPostProcessor"), output)
    }

    @Test
    fun should_pass_when_spring_factories_has_no_environment_post_processor() {
        writeLibrary()
        write(FACTORIES, "org.springframework.context.ApplicationListener=io.codehunters.demo.Listener\n")

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyNoLegacyAliases")?.outcome)
    }

    @Test
    fun should_pass_when_module_has_no_spring_factories() {
        writeLibrary()

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyNoLegacyAliases")?.outcome)
    }

    @Test
    fun should_run_the_alias_check_as_part_of_check() {
        writeLibrary()

        val output = runner("check", "--dry-run").build().output

        assertTrue(output.contains(":verifyNoLegacyAliases SKIPPED"), output)
    }

    private fun runner(vararg arguments: String = arrayOf("verifyNoLegacyAliases")): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")

    private fun writeLibrary() {
        write("settings.gradle.kts", """rootProject.name = "lib"""")
        write("gradle.properties", ROOT_PROPERTIES)
        write("gradle/libs.versions.toml", TestCatalog.CONTENT)
        write("build.gradle.kts", LIBRARY_SCRIPT)
    }

    private fun write(path: String, content: String) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private companion object {
        const val FACTORIES = "src/main/resources/META-INF/spring.factories"
        const val POST_PROCESSOR = "org.springframework.boot.EnvironmentPostProcessor"

        val ROOT_PROPERTIES =
            """
            repository.url=https://example.invalid/java-sdk
            github_packages_url=https://example.invalid/maven
            """.trimIndent()

        val LIBRARY_SCRIPT =
            """
            plugins {
                id("codehunters.java-library")
            }

            version = "1.0.0"
            codehuntersPackages { root = "io.codehunters.demo" }
            """.trimIndent()
    }
}
