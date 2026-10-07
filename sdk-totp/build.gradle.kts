plugins {
    id("codehunters.java-library")
}

version = "1.0.4"
description = "RFC 6238 TOTP generation and verification."

codehuntersPublishing {
    replaces("sdk-utils")
}

dependencies {
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}
