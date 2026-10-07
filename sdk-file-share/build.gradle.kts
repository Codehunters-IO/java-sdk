version = "1.0.3"
description = "HTTP client for the file-share service: presigned upload and download tickets."

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-context")
    compileOnly("tools.jackson.core:jackson-databind")
    compileOnly("org.projectlombok:lombok:${property("lombok.version")}")
    annotationProcessor("org.projectlombok:lombok:${property("lombok.version")}")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-web")
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation("org.mockito:mockito-core")
}
