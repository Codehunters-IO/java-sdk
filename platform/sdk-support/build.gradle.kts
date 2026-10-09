plugins {
    id("codehunters.java-library")
}

version = "1.0.0"
description = "Internal support code shared by the io.codehunters SDK modules; not a public API."

codehuntersPackages {
    root = "io.codehunters.support"
}

dependencies {
    // Callers run inside a Spring Boot application, which already provides these.
    compileOnly("org.springframework.boot:spring-boot")

    testImplementation("org.springframework.boot:spring-boot")
    testImplementation("org.assertj:assertj-core")
}
