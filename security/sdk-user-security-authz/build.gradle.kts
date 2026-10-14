plugins {
    id("codehunters.java-library")
}

version = "6.0.0"
description = "Keycloak Authorization Services policy enforcement point for sdk-user-security."

codehuntersPackages {
    root = "io.codehunters.security.authz"
}

dependencies {
    api(project(":sdk-user-security"))
    implementation(libs.keycloak.policy.enforcer)
    implementation("org.slf4j:slf4j-api")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("io.micrometer:micrometer-core")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework:spring-webmvc")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.mockito:mockito-core")
    testImplementation("io.micrometer:micrometer-core")
}
