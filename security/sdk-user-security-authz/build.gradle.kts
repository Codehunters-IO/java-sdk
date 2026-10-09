plugins {
    id("codehunters.java-library")
}

version = "5.0.0"
description = "Keycloak Authorization Services policy enforcement point for sdk-user-security."

codehuntersPackages {
    root = "io.codehunters.security.authz"
}

dependencies {
    api(project(":sdk-user-security"))
    implementation(libs.keycloak.authz.client)
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework:spring-web")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework:spring-webmvc")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
}
