// The sdk-bom platform: publishes the constraints declared in the module's script and checks that
// they cover exactly the released libraries of this build.
plugins {
    base
    `java-platform`
    id("codehunters.publishing")
}

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            from(components["javaPlatform"])
        }
    }
}

// Constraints are read from the libraries' own versions, so they can't drift; what can drift is
// the list itself: a new module left out, or a SNAPSHOT one left in.
rootProject.subprojects
    .filter { it != project }
    .forEach { evaluationDependsOn(it.path) }

val verifyBomConstraints = tasks.register("verifyBomConstraints") {
    description = "Fails when the BOM misses a released library or constrains a SNAPSHOT one."
    group = JavaBasePlugin.VERIFICATION_GROUP
    // Configured lazily: the constraints are declared in the module's script, after this plugin.
    val libraries = rootProject.subprojects.filter { it.plugins.hasPlugin("codehunters.java-library") }
    val coordinates = libraries.associate { "${it.group}:${it.name}" to it.path }
    val constrained = configurations.getByName("api").dependencyConstraints
        .mapNotNull { coordinates["${it.group}:${it.name}"] }
        .toSet()
    val missing = libraries.filterNot { it.isSnapshot() || it.path in constrained }.map { it.path }.sorted()
    val unreleased = libraries.filter { it.isSnapshot() && it.path in constrained }.map { it.path }.sorted()
    doLast {
        val problems = buildList {
            if (missing.isNotEmpty()) add("released modules missing from the BOM: $missing")
            if (unreleased.isNotEmpty()) add("SNAPSHOT modules in the BOM: $unreleased")
        }
        if (problems.isNotEmpty()) {
            throw GradleException("BOM out of sync with the build — ${problems.joinToString("; ")}")
        }
    }
}

fun Project.isSnapshot() = version.toString().endsWith("-SNAPSHOT")

tasks.named("check") {
    dependsOn(verifyBomConstraints)
}
