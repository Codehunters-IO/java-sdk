version = "2.6.0"
description = "Keycloak-backed request authentication and optional policy enforcement."

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("io.micrometer:micrometer-core")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-aop")
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-webmvc")
    implementation("org.springframework:spring-expression")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.security:spring-security-core")
    implementation("org.springframework.security:spring-security-config")
    implementation("org.springframework.security:spring-security-web")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    implementation("org.keycloak:keycloak-authz-client:${property("keycloak.version")}")
    implementation("tools.jackson.core:jackson-databind")
    implementation("org.aspectj:aspectjrt")
    runtimeOnly("org.aspectj:aspectjweaver")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("ch.qos.logback:logback-classic")
}
