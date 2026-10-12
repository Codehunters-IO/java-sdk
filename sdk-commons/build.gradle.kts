plugins {
    id("codehunters.java-library")
}

version = "4.0.0"
description = "Framework-free helpers: masking, text, passwords, relative time, file names, RFC 6238 TOTP."

codehuntersPackages {
    root = "io.codehunters.commons"
}

// sdk-totp (and sdk-utils before it) ship inside this artifact since 1.1.0 (ADR 0016).
codehuntersPublishing {
    replaces("sdk-totp", "sdk-utils")
}

// The helpers depend on the JDK only. Spring Boot stays compileOnly: the TOTP auto-configuration
// loads only in a Boot app.
dependencies {
    testImplementation(libs.archunit.junit5)
}
