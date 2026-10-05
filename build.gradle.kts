plugins {
    alias(libs.plugins.sonarqube)
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
