plugins {
    id("codehunters.java-library")
}

version = "5.1.5"
description = "Transactional outbox with claim-based dispatch to RabbitMQ or Kafka."

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework:spring-tx")
    compileOnly("org.springframework.amqp:spring-rabbit")
    compileOnly("org.springframework.kafka:spring-kafka")
    compileOnly("org.springframework:spring-context")
    compileOnly("io.micrometer:micrometer-core")
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.provider.jdbc.template)
    compileOnly("tools.jackson.core:jackson-databind")
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-jackson")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.springframework.amqp:spring-rabbit")
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation("org.mockito:mockito-core")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.postgresql:postgresql")
}
