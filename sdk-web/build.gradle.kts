plugins {
    id("codehunters.java-library")
}

version = "2.0.0"
description = "Servlet web toolkit: RFC 9457 problem details, client IP and User-Agent, X-Locale i18n."

codehuntersPackages {
    root = "io.codehunters.web"
}

// One artifact for what were sdk-web-errors, sdk-device and sdk-i18n (ADR 0016). Each part keeps
// its own auto-configuration, package, properties and on/off switch.
codehuntersPublishing {
    replaces("sdk-web-errors", "sdk-device", "sdk-i18n")
}

dependencies {
    implementation(project(":sdk-support"))
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("org.springframework.boot:spring-boot-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("org.springframework.security:spring-security-web")
    compileOnly("org.springframework.security:spring-security-oauth2-resource-server")
    compileOnly("tools.jackson.core:jackson-databind")
    compileOnly(project(":sdk-tracing"))
    compileOnly(libs.uap.java)

    testImplementation(project(":sdk-tracing"))
    testImplementation(project(":sdk-commons"))
    testImplementation("org.springframework:spring-tx")
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
    testImplementation(libs.uap.java)
    testImplementation(libs.archunit.junit5)
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.mockito:mockito-core")
}
