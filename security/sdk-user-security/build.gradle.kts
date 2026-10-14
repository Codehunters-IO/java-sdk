plugins {
    id("codehunters.java-library")
}

version = "6.0.0"
description = "Keycloak-backed request authentication for servlet applications."

codehuntersPackages {
    root = "io.codehunters.security"
}

dependencies {
    api("org.springframework.security:spring-security-config")
    api("org.springframework.security:spring-security-web")
    api("org.springframework.security:spring-security-oauth2-resource-server")
    api("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework:spring-web")
    implementation("org.slf4j:slf4j-api")
    implementation("tools.jackson.core:jackson-databind")
    implementation("com.github.ben-manes.caffeine:caffeine")
    // Optional: introspection metrics are published only when the application has Micrometer.
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-webmvc")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-security")
    testImplementation("org.springframework.boot:spring-boot-security-oauth2-resource-server")
    testImplementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("ch.qos.logback:logback-classic")
    testImplementation(project(":sdk-web"))
    testImplementation("org.springframework.boot:spring-boot-webmvc")
    testImplementation("org.springframework.boot:spring-boot-jackson")
    testImplementation("com.jayway.jsonpath:json-path")
}
