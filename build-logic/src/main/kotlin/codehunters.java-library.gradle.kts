// Conventions for every SDK library: Java 21, Boot BOM kept internal, Spotless, JaCoCo gates.
plugins {
    `java-library`
    jacoco
    id("com.diffplug.spotless")
    id("codehunters.publishing")
}

val libs = the<VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
}

// Boot's BOM aligns versions inside this build only. It is kept out of apiElements and
// runtimeElements so consumers don't inherit Boot constraints; published metadata carries
// the resolved versions instead (see versionMapping below).
val bootPlatform = configurations.create("bootPlatform") {
    isCanBeConsumed = false
    isCanBeResolved = false
}
listOf(
    "compileClasspath",
    "runtimeClasspath",
    "annotationProcessor",
    "testCompileClasspath",
    "testRuntimeClasspath",
    "testAnnotationProcessor",
).forEach { configurations.named(it) { extendsFrom(bootPlatform) } }

dependencies {
    "bootPlatform"(platform(libs.findLibrary("spring-boot-dependencies").get()))

    "compileOnly"("org.springframework.boot:spring-boot-autoconfigure")
    "annotationProcessor"("org.springframework.boot:spring-boot-configuration-processor")

    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        googleJavaFormat(libs.findVersion("google-java-format").get().requiredVersion)
        target("src/**/*.java")
        trimTrailingWhitespace()
        endWithNewline()
        removeUnusedImports()
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    finalizedBy("jacocoTestReport")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Property records are pure binding; keep this list in sync with sonar.coverage.exclusions.
val jacocoExclusions = listOf("**/*Properties*")

fun org.gradle.testing.jacoco.tasks.JacocoReportBase.applyExclusions() {
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(jacocoExclusions) } })
    )
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    applyExclusions()
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    applyExclusions()
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// The gates are the same for every module. The old per-module override is rejected rather than
// silently ignored, so nobody believes a lowered bar is still in effect. Project properties, not
// gradleProperty(): only they include the module's own gradle.properties, where the override lived.
val coverageOverride = findProperty("coverage.branch.minimum")?.toString()
val verifyCoverageGates = tasks.register("verifyCoverageGates") {
    description = "Fails when a module tries to override the shared JaCoCo coverage gates."
    group = JavaBasePlugin.VERIFICATION_GROUP
    val override = coverageOverride
    val projectPath = project.path
    doLast {
        if (override != null) {
            throw GradleException(
                "$projectPath sets coverage.branch.minimum=$override, but the coverage gates are fixed " +
                    "(LINE 0.90, BRANCH 0.80). Remove the property and add the missing tests."
            )
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"), verifyCoverageGates)
}

tasks.register("jacocoLogCodeCoverage") {
    description = "Logs code coverage summary from JaCoCo XML report."
    group = JavaBasePlugin.VERIFICATION_GROUP
    dependsOn("jacocoTestReport")

    doLast {
        val reportFile = file("${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml")
        if (!reportFile.exists()) {
            println("JaCoCo report not found: $reportFile")
            return@doLast
        }

        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder().parse(reportFile)

        var instrMissed = 0L; var instrCovered = 0L
        var branchMissed = 0L; var branchCovered = 0L
        var lineMissed = 0L; var lineCovered = 0L

        val counters = doc.documentElement.getElementsByTagName("counter")
        for (i in 0 until counters.length) {
            val counter = counters.item(i) as org.w3c.dom.Element
            if (counter.parentNode != doc.documentElement) continue
            val missed = counter.getAttribute("missed").toLong()
            val covered = counter.getAttribute("covered").toLong()
            when (counter.getAttribute("type")) {
                "INSTRUCTION" -> { instrMissed += missed; instrCovered += covered }
                "BRANCH" -> { branchMissed += missed; branchCovered += covered }
                "LINE" -> { lineMissed += missed; lineCovered += covered }
            }
        }

        fun pct(covered: Long, missed: Long): String {
            val total = covered + missed
            return if (total == 0L) "N/A" else String.format("%.2f%%", covered * 100.0 / total)
        }

        val separator = "-".repeat(70)
        println("\n$separator")
        println("JaCoCo Code Coverage Summary — ${project.name}")
        println(separator)
        println(String.format("  %-20s %12s %12s %12s", "", "Instruction%", "Branch%", "Line%"))
        println(separator)
        println(String.format("  %-20s %12s %12s %12s",
            project.name, pct(instrCovered, instrMissed), pct(branchCovered, branchMissed), pct(lineCovered, lineMissed)))
        println("$separator\n")
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

// The configuration processor merges META-INF/additional-spring-configuration-metadata.json only
// when it can see it while compiling, so resources must be processed first.
tasks.named<JavaCompile>("compileJava") {
    inputs.files(tasks.named("processResources"))
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all", "-quiet")
        encoding = "UTF-8"
    }
}

publishing {
    publications {
        // groupId/artifactId/version default to the project's, read lazily: setting them here
        // runs before each module's script assigns `version`, which published "unspecified".
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            versionMapping {
                allVariants { fromResolutionResult() }
            }
        }
    }
}
