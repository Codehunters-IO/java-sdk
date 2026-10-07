plugins {
    id("codehunters.java-library")
}

version = "2.0.0"
description = "RFC 6238 TOTP generation and verification."

codehuntersPublishing {
    replaces("sdk-utils")
}

dependencies {
    implementation(project(":sdk-support"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}
