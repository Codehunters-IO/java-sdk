plugins {
    id("codehunters.java-library")
}

version = "9.0.0"
description = "PostgreSQL outbox store and ShedLock-protected dispatch and purge schedulers."

codehuntersPackages {
    root = "io.codehunters.messaging.jdbc"
}

dependencies {
    api(project(":sdk-event-messaging-core"))
    api(libs.shedlock.spring)
    api(libs.shedlock.provider.jdbc.template)
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework:spring-tx")
    compileOnly("org.springframework:spring-context")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("tools.jackson.core:jackson-databind")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-jackson")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework:spring-tx")
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation("org.mockito:mockito-core")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.postgresql:postgresql")
}
