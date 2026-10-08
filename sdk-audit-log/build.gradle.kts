plugins {
    id("codehunters.java-library")
}

version = "3.1.0"
description = "Declarative @Auditable audit trail with SpEL context and an async JDBC adapter."

dependencies {
    implementation(project(":sdk-support"))
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-aop")
    implementation("org.springframework:spring-expression")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.aspectj:aspectjrt")
    runtimeOnly("org.aspectj:aspectjweaver")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework:spring-tx")
    compileOnly("tools.jackson.core:jackson-databind")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("io.projectreactor:reactor-core")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("jakarta.transaction:jakarta.transaction-api")
    compileOnly(project(":sdk-web"))

    testImplementation("org.springframework:spring-test")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.springframework.boot:spring-boot-jdbc")
    testImplementation("org.springframework.boot:spring-boot-jackson")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("io.projectreactor:reactor-core")
    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("jakarta.transaction:jakarta.transaction-api")
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation(project(":sdk-web"))
    testImplementation("org.springframework:spring-web")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-mssqlserver")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.microsoft.sqlserver:mssql-jdbc")
}
