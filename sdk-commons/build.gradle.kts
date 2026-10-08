plugins {
    id("codehunters.java-library")
}

version = "1.1.0"
description = "Framework-free helpers: masking, text, passwords, relative time, file names, RFC 6238 TOTP."

// sdk-totp (and sdk-utils before it) ship inside this artifact since 1.1.0 (ADR 0016).
codehuntersPublishing {
    replaces("sdk-totp", "sdk-utils")
}

// The helpers depend on the JDK only. TOTP brings sdk-support at runtime, for its legacy property
// aliases; Spring Boot stays compileOnly and its auto-configuration loads only in a Boot app.
dependencies {
    implementation(project(":sdk-support"))

    testImplementation(libs.archunit.junit5)
}
