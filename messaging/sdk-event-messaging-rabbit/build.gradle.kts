plugins {
    id("codehunters.java-library")
}

version = "9.0.0"
description = "RabbitMQ publisher for the transactional outbox."

codehuntersPackages {
    root = "io.codehunters.messaging.rabbit"
}

dependencies {
    api(project(":sdk-event-messaging-core"))
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.amqp:spring-rabbit")
    compileOnly("org.springframework:spring-context")
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation(project(":sdk-event-messaging-jdbc"))
    testImplementation(project(":sdk-event-messaging-kafka"))
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.springframework.amqp:spring-rabbit")
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
}
