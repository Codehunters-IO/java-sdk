plugins {
    id("codehunters.java-library")
}

version = "2.0.0"
description = "JPA building blocks: audited UUIDv7 entities, page responses, JSON converters, safe search specifications."

dependencies {
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    compileOnly("org.hibernate.orm:hibernate-core")
    compileOnly("org.springframework.data:spring-data-commons")
    compileOnly("org.springframework.data:spring-data-jpa")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("tools.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation(libs.archunit.junit5)
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.h2database:h2")
}
