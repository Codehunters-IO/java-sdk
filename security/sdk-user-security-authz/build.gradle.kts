plugins {
    id("codehunters.java-library")
}

version = "3.0.0"
description = "Keycloak Authorization Services policy enforcement point for sdk-user-security."

dependencies {
    api(project(":sdk-user-security"))
    implementation(project(":sdk-support"))
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
