import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PackageLayoutPluginTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun should_fail_when_module_declares_no_package_root() {
        writeLibrary("")

        val output = runner().buildAndFail().output

        assertTrue(output.contains("codehuntersPackages"), output)
    }

    @Test
    fun should_fail_when_module_has_a_hexagonal_package() {
        writeLibrary(ROOT_BLOCK)
        write("src/main/java/io/codehunters/demo/domain/Thing.java", "package io.codehunters.demo.domain;")
        write("src/main/java/io/codehunters/demo/domain/package-info.java", "package io.codehunters.demo.domain;")

        val output = runner().buildAndFail().output

        assertTrue(output.contains("io.codehunters.demo.domain"), output)
    }

    @Test
    fun should_fail_when_module_puts_classes_under_another_modules_root() {
        write("settings.gradle.kts", """include("core", "jdbc")""")
        write("gradle.properties", ROOT_PROPERTIES)
        write("gradle/libs.versions.toml", TestCatalog.CONTENT)
        write("core/build.gradle.kts", libraryScript("""codehuntersPackages { root = "io.codehunters.demo" }"""))
        write("jdbc/build.gradle.kts", libraryScript("""codehuntersPackages { root = "io.codehunters.demo.jdbc" }"""))
        write("core/src/main/java/io/codehunters/demo/jdbc/Leak.java", "package io.codehunters.demo.jdbc;")
        write("core/src/main/java/io/codehunters/demo/jdbc/package-info.java", "package io.codehunters.demo.jdbc;")

        val output = runner(":core:verifyPackageLayout").buildAndFail().output

        assertTrue(output.contains("another module"), output)
    }

    @Test
    fun should_fail_when_two_modules_declare_the_same_root() {
        write("settings.gradle.kts", """include("core", "copy")""")
        write("gradle.properties", ROOT_PROPERTIES)
        write("gradle/libs.versions.toml", TestCatalog.CONTENT)
        write("core/build.gradle.kts", libraryScript(ROOT_BLOCK))
        write("copy/build.gradle.kts", libraryScript(ROOT_BLOCK))

        val output = runner(":core:verifyPackageLayout").buildAndFail().output

        assertTrue(output.contains("another module also declares"), output)
    }

    @Test
    fun should_be_up_to_date_when_sources_did_not_change() {
        writeLibrary(ROOT_BLOCK)
        write("src/main/java/io/codehunters/demo/Thing.java", "package io.codehunters.demo;")
        write("src/main/java/io/codehunters/demo/package-info.java", "package io.codehunters.demo;")
        runner().build()

        val result = runner().build()

        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":verifyPackageLayout")?.outcome)
    }

    @Test
    fun should_reuse_the_configuration_cache_when_the_layout_check_runs_twice() {
        writeLibrary(ROOT_BLOCK)
        write("src/main/java/io/codehunters/demo/Thing.java", "package io.codehunters.demo;")
        write("src/main/java/io/codehunters/demo/package-info.java", "package io.codehunters.demo;")
        runner("verifyPackageLayout", "--configuration-cache").build()

        val output = runner("verifyPackageLayout", "--configuration-cache").build().output

        assertTrue(output.contains("Reusing configuration cache"), output)
    }

    @Test
    fun should_pass_when_packages_follow_the_convention() {
        writeLibrary(ROOT_BLOCK)
        write("src/main/java/io/codehunters/demo/Thing.java", "package io.codehunters.demo;")
        write("src/main/java/io/codehunters/demo/package-info.java", "package io.codehunters.demo;")

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyPackageLayout")?.outcome)
    }

    @Test
    fun should_run_the_layout_check_as_part_of_check() {
        writeLibrary(ROOT_BLOCK)

        val output = runner("check", "--dry-run").build().output

        assertTrue(output.contains(":verifyPackageLayout SKIPPED"), output)
    }

    private fun runner(vararg arguments: String = arrayOf("verifyPackageLayout")): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")

    private fun writeLibrary(extra: String) {
        write("settings.gradle.kts", """rootProject.name = "lib"""")
        write("gradle.properties", ROOT_PROPERTIES)
        write("gradle/libs.versions.toml", TestCatalog.CONTENT)
        write("build.gradle.kts", libraryScript(extra))
    }

    private fun write(path: String, content: String) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private companion object {
        const val ROOT_BLOCK = """codehuntersPackages { root = "io.codehunters.demo" }"""

        val ROOT_PROPERTIES =
            """
            repository.url=https://example.invalid/java-sdk
            github_packages_url=https://example.invalid/maven
            """.trimIndent()

        fun libraryScript(extra: String) =
            """
            plugins {
                id("codehunters.java-library")
            }

            version = "1.0.0"
            $extra
            """.trimIndent()
    }
}
