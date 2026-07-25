version = "5.1.5"

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework:spring-tx")
    compileOnly("org.springframework.amqp:spring-rabbit")
    compileOnly("org.springframework:spring-context")
    implementation("net.javacrumbs.shedlock:shedlock-spring:${property("shedlock.version")}")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:${property("shedlock.version")}")
    compileOnly("tools.jackson.core:jackson-databind")
    compileOnly("org.projectlombok:lombok:${property("lombok.version")}")
    annotationProcessor("org.projectlombok:lombok:${property("lombok.version")}")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.springframework.amqp:spring-rabbit")
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation("org.mockito:mockito-core")
    testRuntimeOnly("com.h2database:h2")
}
