plugins {
    id("codehunters.java-library")
}

version = "3.0.0"
description = "Legacy gateway trace headers and a TraceContextPort on top of Micrometer Tracing."

codehuntersPackages {
    root = "io.codehunters.tracing"
}

dependencies {
    api("io.micrometer:micrometer-tracing")

    compileOnly("org.springframework.boot:spring-boot")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.slf4j:slf4j-api")
    compileOnly("io.opentelemetry:opentelemetry-api")
    compileOnly("io.opentelemetry:opentelemetry-sdk-trace")
    compileOnly("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")

    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    testImplementation("io.micrometer:micrometer-tracing-bridge-otel")
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
}
