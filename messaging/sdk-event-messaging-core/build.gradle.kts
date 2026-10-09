plugins {
    id("codehunters.java-library")
}

version = "9.0.0"
description = "Transactional outbox domain, ports, dispatch use case and core auto-configuration."

codehuntersPackages {
    root = "io.codehunters.messaging"
}

codehuntersPublishing {
    replaces("sdk-event-messaging")
}

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-context")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("io.micrometer:micrometer-tracing")
    compileOnly(project(":sdk-tracing"))
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation("org.springframework:spring-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("io.micrometer:micrometer-tracing-bridge-otel")
    testImplementation("io.opentelemetry:opentelemetry-sdk-trace")
    testImplementation(project(":sdk-tracing"))
    testImplementation(libs.archunit.junit5)
}
