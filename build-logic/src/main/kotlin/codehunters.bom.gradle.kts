// The sdk-bom platform: publishes the constraints declared in the module's script, checks that they
// cover exactly the released libraries of this build, and that a released BOM version never changes.
plugins {
    base
    `java-platform`
    id("codehunters.publishing")
}

val codehuntersBom = extensions.create<CodehuntersBomExtension>("codehuntersBom")

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            from(components["javaPlatform"])
        }
    }
}

rootProject.subprojects
    .filter { it != project }
    .forEach { evaluationDependsOn(it.path) }

// Providers, read when the tasks run (or when the configuration cache is stored): the constraints
// and exclusions are declared in the module's script, after this plugin is applied. Task actions
// only use locals copied from these: a reference to a script property can't be cached.
val libraryVersions = provider {
    rootProject.subprojects
        .filter { it.plugins.hasPlugin("codehunters.java-library") }
        .associate { it.path to it.version.toString() }
}
val constrainedModules = provider {
    val byCoordinates = rootProject.subprojects.associateBy { "${it.group}:${it.name}" }
    configurations.getByName("api").dependencyConstraints
        .mapNotNull { byCoordinates["${it.group}:${it.name}"] }
        .associate { it.path to (it.name to it.version.toString()) }
}
val constrainedModuleVersions = constrainedModules.map { modules -> modules.values.toMap() }
val currentBomVersion = provider { version.toString() }
val bomLockFile = layout.projectDirectory.file("bom-lock.properties").asFile

val verifyBomConstraints = tasks.register("verifyBomConstraints") {
    description = "Fails when the BOM misses a released library, or constrains a SNAPSHOT or excluded one."
    group = JavaBasePlugin.VERIFICATION_GROUP
    val libraries = libraryVersions
    val constrained = constrainedModules
    val excluded = codehuntersBom.excludedModules
    doLast {
        val all = libraries.get()
        val inBom = constrained.get().keys
        val skipped = excluded.get()
        val released = all.filterValues { !it.endsWith("-SNAPSHOT") }.keys
        val problems = buildList {
            val missing = (released - inBom - skipped).sorted()
            val unreleased = (all.keys - released).filter { it in inBom }.sorted()
            val leaving = skipped.filter { it in inBom }.sorted()
            if (missing.isNotEmpty()) add("released modules missing from the BOM: $missing")
            if (unreleased.isNotEmpty()) add("SNAPSHOT modules in the BOM: $unreleased")
            if (leaving.isNotEmpty()) add("excluded modules in the BOM: $leaving")
        }
        if (problems.isNotEmpty()) {
            throw GradleException("BOM out of sync with the build — ${problems.joinToString("; ")}")
        }
    }
}

val verifyBomLock = tasks.register("verifyBomLock") {
    description = "Fails when a released BOM version would constrain other versions than it was released with."
    group = JavaBasePlugin.VERIFICATION_GROUP
    val bomPath = project.path
    val bomVersion = currentBomVersion
    val lockFile = bomLockFile
    val constrainedVersions = constrainedModuleVersions
    doLast {
        val version = bomVersion.get()
        if (version.endsWith("-SNAPSHOT")) return@doLast
        val locked = BomLock.read(lockFile)
            ?: throw GradleException("${lockFile.name} is missing; run $bomPath:updateBomLock and commit it.")
        if (locked[BomLock.VERSION_KEY] != version) {
            throw GradleException(
                "$bomPath is $version but ${lockFile.name} records ${locked[BomLock.VERSION_KEY]}; " +
                    "run $bomPath:updateBomLock and commit it."
            )
        }
        val changes = BomLock.changes(locked, constrainedVersions.get())
        if (changes.isNotEmpty()) {
            throw GradleException(
                "$bomPath $version was released with other versions (${changes.joinToString()}); " +
                    "bump the version of $bomPath, then run $bomPath:updateBomLock."
            )
        }
    }
}

tasks.register("updateBomLock") {
    description = "Records the versions this BOM version constrains; refuses to rewrite a released version."
    group = PublishingPlugin.PUBLISH_TASK_GROUP
    val bomPath = project.path
    val bomVersion = currentBomVersion
    val lockFile = bomLockFile
    val constrainedVersions = constrainedModuleVersions
    doLast {
        val version = bomVersion.get()
        val modules = constrainedVersions.get()
        val locked = BomLock.read(lockFile)
        val sameRelease = locked != null && !version.endsWith("-SNAPSHOT") && locked[BomLock.VERSION_KEY] == version
        if (sameRelease && BomLock.changes(locked!!, modules).isNotEmpty()) {
            throw GradleException(
                "${lockFile.name} already records $bomPath $version with other versions; " +
                    "bump the version of $bomPath first."
            )
        }
        lockFile.writeText(BomLock.render(bomPath, version, modules))
    }
}

tasks.named("check") {
    dependsOn(verifyBomConstraints, verifyBomLock)
}
