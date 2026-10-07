plugins {
    id("codehunters.java-library")
}

version = "1.0.0"
description = "RFC 9457 problem details for Spring MVC: stable error codes, localized messages, no leaks."

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("org.springframework.boot:spring-boot-webmvc")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("org.springframework.security:spring-security-web")
    compileOnly("org.springframework.security:spring-security-oauth2-resource-server")
    compileOnly("tools.jackson.core:jackson-databind")
    compileOnly(project(":sdk-tracing"))

    testImplementation(project(":sdk-tracing"))
    testImplementation("org.springframework.boot:spring-boot-webmvc")
    testImplementation("org.springframework.boot:spring-boot-http-converter")
    testImplementation("org.springframework.boot:spring-boot-jackson")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework:spring-webmvc")
    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.security:spring-security-config")
    testImplementation("org.springframework.security:spring-security-web")
    testImplementation("org.springframework.security:spring-security-oauth2-resource-server")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation(libs.archunit.junit5)
}

// Spring MVC binds @RequestParam/@PathVariable by parameter name, and violations report it.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}
