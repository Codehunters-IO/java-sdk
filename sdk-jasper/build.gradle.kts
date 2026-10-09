plugins {
    id("codehunters.java-library")
}

version = "2.1.0"
description = "JasperReports 7 rendering to PDF, XLSX or CSV from classpath templates, with limits."

codehuntersPackages {
    root = "io.codehunters.jasper"
}

dependencies {
    api(libs.jasperreports)
    // jasperreports-pdf pins a Jaspersoft build of OpenPDF that only their own repository serves;
    // the same OpenPDF release from Maven Central keeps consumers on Central alone.
    implementation(libs.jasperreports.pdf) {
        exclude(group = "com.github.librepdf", module = "openpdf")
    }
    implementation(libs.openpdf)
    // Compiles .jrxml expressions with ecj, which reads classes through the class loader; javac, the
    // fallback, only sees java.class.path and fails inside a Spring Boot fat jar.
    implementation(libs.jasperreports.jdt)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("io.micrometer:micrometer-core")

    // JasperReports 7.0.8 reads JRXML with Jackson 2; Boot's BOM pins a release with open advisories.
    constraints {
        implementation(libs.jackson2.core)
        implementation(libs.jackson2.databind)
        implementation(libs.jackson2.dataformat.xml)
    }

    testImplementation(libs.pdfbox)
    testImplementation("io.micrometer:micrometer-core")
    testImplementation(libs.archunit.junit5)
    testImplementation("org.awaitility:awaitility")
}

// Reports lay text out with java.awt fonts; the build proves they need no display.
tasks.named<Test>("test") {
    systemProperty("java.awt.headless", "true")
}
