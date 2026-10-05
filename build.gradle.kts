plugins {
    id("com.diffplug.spotless") apply false
    id("org.sonarqube")
}

allprojects {
    group = "io.codehunters"
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "maven-publish")
    apply(plugin = "jacoco")

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
        "bootPlatform"(platform("org.springframework.boot:spring-boot-dependencies:${property("spring.boot.version")}"))

        "compileOnly"("org.springframework.boot:spring-boot-autoconfigure")
        "annotationProcessor"("org.springframework.boot:spring-boot-configuration-processor")

        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withJavadocJar()
        withSourcesJar()
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat("${property("google.java.format.version")}")
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
            }
        }
    }

    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
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

    tasks.withType<Javadoc> {
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:all", "-quiet")
            encoding = "UTF-8"
        }
    }

    configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("${rootProject.findProperty("github_packages_url")}")
                credentials {
                    username = System.getenv("GH_PACKAGES_USERNAME")
                    password = System.getenv("GH_PACKAGES_TOKEN")
                }
            }
        }

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
}

sonar {
    properties {
        property("sonar.projectKey", "codehunters_java-sdk")
        property("sonar.organization", "codehunters")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.token", System.getenv("SONAR_TOKEN") ?: "")
        property("sonar.java.coveragePlugin", "jacoco")
        property("sonar.gradle.skipCompile", "true")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            subprojects.joinToString(",") { "${it.layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml" }
        )
        property("sonar.exclusions", "**/src/test/**,**/*Test.java,**/*IT.java")
        property("sonar.coverage.exclusions", "**/config/**")
        property("sonar.cpd.exclusions", "**/*Config*")
    }
}
