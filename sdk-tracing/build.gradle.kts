plugins {
    id("codehunters.java-library")
}

version = "0.2.3"
description = "Trace id propagation across HTTP, async tasks and RabbitMQ through the MDC."

dependencies {
    compileOnly("org.springframework.boot:spring-boot")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework.amqp:spring-rabbit")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.slf4j:slf4j-api")
    compileOnly("tools.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework.amqp:spring-rabbit")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.mockito:mockito-core")
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("io.micrometer:micrometer-tracing")
}
