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
        write("bom/build.gradle.kts", bom(modules = listOf(":lib-a")))

        val output = runner().buildAndFail().output

        assertTrue(output.contains("missing from the BOM: [:lib-b]"), output)
    }

    @Test
    fun should_fail_when_the_bom_constrains_a_snapshot_module() {
        write("bom/build.gradle.kts", bom(modules = listOf(":lib-a", ":lib-b", ":snapshot-lib")))

        val output = runner().buildAndFail().output

        assertTrue(output.contains("SNAPSHOT modules in the BOM: [:snapshot-lib]"), output)
    }

    @Test
    fun should_pass_when_the_bom_constrains_every_released_module() {
        write("bom/build.gradle.kts", bom())

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":bom:verifyBomConstraints")?.outcome)
    }

    @Test
    fun should_not_require_a_released_module_the_bom_excludes() {
        write("settings.gradle.kts", """include("lib-a", "lib-b", "leaving-lib", "bom")""")
        write("leaving-lib/build.gradle.kts", library("3.0.0"))
        write("bom/build.gradle.kts", bom(excluded = listOf(":leaving-lib")))

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":bom:verifyBomConstraints")?.outcome)
    }

    @Test
    fun should_fail_when_the_bom_constrains_an_excluded_module() {
        write("bom/build.gradle.kts", bom(excluded = listOf(":lib-b")))

        val output = runner().buildAndFail().output

        assertTrue(output.contains("excluded modules in the BOM: [:lib-b]"), output)
    }

    @Test
    fun should_publish_the_platform_with_the_constrained_versions() {
        write("bom/build.gradle.kts", bom())

        runner(":bom:generatePomFileForMavenBomPublication").build()

        val pom = File(projectDir, "bom/build/publications/mavenBom/pom-default.xml").readText()
        val compact = pom.replace(Regex("\\s+"), "")
        assertTrue(compact.contains("<artifactId>lib-b</artifactId><version>2.0.0</version>"), pom)
        assertFalse(pom.contains("snapshot-lib"), pom)
    }

    @Test
    fun should_publish_module_metadata_with_the_constrained_versions() {
        write("bom/build.gradle.kts", bom())

        runner(":bom:generateMetadataFileForMavenBomPublication").build()

        val metadata = File(projectDir, "bom/build/publications/mavenBom/module.json").readText()
        val compact = metadata.replace(Regex("\\s+"), "")
        assertTrue(compact.contains(""""module":"lib-b","version":{"requires":"2.0.0"}"""), metadata)
    }

    @Test
    fun should_pass_when_the_lock_matches_the_bom() {
        write("bom/build.gradle.kts", bom())
        write("bom/bom-lock.properties", lock(bomVersion = "1.0.0", libB = "2.0.0"))

        val result = runner(":bom:verifyBomLock").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":bom:verifyBomLock")?.outcome)
    }

    @Test
    fun should_fail_when_a_constrained_module_changes_under_the_same_bom_version() {
        write("bom/build.gradle.kts", bom())
        write("bom/bom-lock.properties", lock(bomVersion = "1.0.0", libB = "1.9.0"))

        val output = runner(":bom:verifyBomLock").buildAndFail().output

        assertTrue(output.contains("lib-b 1.9.0 -> 2.0.0"), output)
        assertTrue(output.contains("bump the version of :bom"), output)
    }

    @Test
    fun should_fail_when_the_bom_version_changes_without_updating_the_lock() {
        write("bom/build.gradle.kts", bom())
        write("bom/bom-lock.properties", lock(bomVersion = "0.9.0", libB = "2.0.0"))

        val output = runner(":bom:verifyBomLock").buildAndFail().output

        assertTrue(output.contains("run :bom:updateBomLock"), output)
    }

    @Test
    fun should_fail_when_the_lock_is_missing() {
        write("bom/build.gradle.kts", bom())

        val output = runner(":bom:verifyBomLock").buildAndFail().output

        assertTrue(output.contains("bom-lock.properties is missing"), output)
    }

    @Test
    fun should_skip_the_lock_check_for_a_snapshot_bom() {
        write("bom/build.gradle.kts", bom(version = "1.1.0-SNAPSHOT"))

        val result = runner(":bom:verifyBomLock").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":bom:verifyBomLock")?.outcome)
    }

    @Test
    fun should_write_the_lock_when_the_bom_version_was_bumped() {
        write("bom/build.gradle.kts", bom())
        write("bom/bom-lock.properties", lock(bomVersion = "0.9.0", libB = "1.9.0"))

        runner(":bom:updateBomLock").build()

        assertEquals(lock(bomVersion = "1.0.0", libB = "2.0.0"), read("bom/bom-lock.properties"))
    }

    @Test
    fun should_refuse_to_update_the_lock_when_modules_change_under_the_same_bom_version() {
        write("bom/build.gradle.kts", bom())
        write("bom/bom-lock.properties", lock(bomVersion = "1.0.0", libB = "1.9.0"))

        val output = runner(":bom:updateBomLock").buildAndFail().output

        assertTrue(output.contains("bump the version of :bom"), output)
    }

    @Test
    fun should_run_both_bom_checks_as_part_of_check() {
        write("bom/build.gradle.kts", bom())

        val output = runner(":bom:check", "--dry-run").build().output

        assertTrue(output.contains(":bom:verifyBomConstraints SKIPPED"), output)
        assertTrue(output.contains(":bom:verifyBomLock SKIPPED"), output)
    }

    @Test
    fun should_support_the_configuration_cache() {
        write("bom/build.gradle.kts", bom())
        write("bom/bom-lock.properties", lock(bomVersion = "1.0.0", libB = "2.0.0"))

        val result = runner(":bom:verifyBomConstraints", ":bom:verifyBomLock", "--configuration-cache").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":bom:verifyBomLock")?.outcome)
    }

    private fun runner(vararg arguments: String = arrayOf(":bom:verifyBomConstraints")): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")

    private fun write(path: String, content: String) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun read(path: String) = File(projectDir, path).readText()

    private fun library(version: String) =
        """
        plugins {
            id("codehunters.java-library")
        }

        version = "$version"
        """.trimIndent()

    private fun bom(
        version: String = "1.0.0",
        modules: List<String> = listOf(":lib-a", ":lib-b"),
        excluded: List<String> = emptyList(),
    ) =
        """
        plugins {
            id("codehunters.bom")
        }

        version = "$version"

        codehuntersBom {
            ${excluded.joinToString("\n") { "excludedModules.add(\"$it\")" }}
        }

        dependencies {
            constraints {
                ${modules.joinToString("\n") { "api(project(\"$it\"))" }}
            }
        }
        """.trimIndent()

    private fun lock(bomVersion: String, libB: String) =
        """
        # Versions :bom was released with. Regenerate with ./gradlew :bom:updateBomLock after bumping it.
        bom.version=$bomVersion
        lib-a=1.0.0
        lib-b=$libB
        """.trimIndent() + "\n"

    private companion object {
        val ROOT_PROPERTIES =
            """
            repository.url=https://example.invalid/java-sdk
            github_packages_url=https://example.invalid/maven
            """.trimIndent()
    }
}
