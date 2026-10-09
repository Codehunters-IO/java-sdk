plugins {
    id("codehunters.java-library")
}

version = "2.0.0"
description = "Geofencing: decides whether a point falls inside the zones of a caller-defined scope."

codehuntersPackages {
    root = "io.codehunters.geo"
}

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-jdbc")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework:spring-jdbc")
    testImplementation(libs.archunit.junit5)
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
