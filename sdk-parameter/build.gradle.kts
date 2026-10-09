plugins {
    id("codehunters.java-library")
}

version = "4.0.0"
description = "Typed read access to the shared parameters table over JDBC."

codehuntersPackages {
    root = "io.codehunters.parameter"
}

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework:spring-context")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.awaitility:awaitility")
    testImplementation(libs.archunit.junit5)
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
