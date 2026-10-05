plugins {
    id("codehunters.java-library")
}

version = "6.0.0"
description = "Transactional outbox domain, ports, dispatch use case and core auto-configuration."

codehuntersPublishing {
    replaces("sdk-event-messaging")
}

dependencies {
    implementation(project(":sdk-support"))
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-context")
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation("org.springframework:spring-test")
    testImplementation("org.mockito:mockito-core")
}
