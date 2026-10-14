plugins {
    id("codehunters.java-library")
}

version = "5.0.0"
description = "HTTP client for the file-share service: presigned upload and download tickets."

codehuntersPackages {
    root = "io.codehunters.fileshare"
}

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-context")
    compileOnly("tools.jackson.core:jackson-databind")
    // Optional: outbound client credentials (codehunters.file-share.auth.client-registration-id).
    compileOnly("org.springframework.security:spring-security-oauth2-client")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-web")
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation("org.springframework.security:spring-security-oauth2-client")
    testImplementation("org.mockito:mockito-core")
    // Spring Security's request-bound OAuth2AuthorizedClientManager needs the servlet API to load.
    testImplementation("jakarta.servlet:jakarta.servlet-api")
}
