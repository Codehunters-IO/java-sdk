plugins {
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.dependency.check)
}

// Build conventions live in build-logic/ (codehunters.java-library, codehunters.publishing).
val libraries = subprojects.filter { it.name != "sdk-bom" }

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
            libraries.joinToString(",") { "${it.layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml" }
        )
        property("sonar.exclusions", "**/src/test/**,**/*Test.java,**/*IT.java")
        property("sonar.coverage.exclusions", "**/*Properties.java")
        property("sonar.cpd.exclusions", "**/*Config*")
    }
}

// OWASP dependency check over every module's dependencies, in one report under
// build/reports/dependency-check/ (plugin 13; earlier versions wrote build/reports/). The shared
// java-owasp workflow runs
// `dependencyCheckAnalyze` at the root, so that task delegates to the aggregate one: the root
// project has no dependencies of its own to analyze.
dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    suppressionFile = file("config/dependency-check/suppressions.xml").path
    nvd {
        apiKey = System.getProperty("nvdApiKey") ?: System.getenv("NVD_API_KEY")
    }
}

tasks.named("dependencyCheckAnalyze") {
    enabled = false
    dependsOn("dependencyCheckAggregate")
}
