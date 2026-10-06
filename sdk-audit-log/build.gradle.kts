plugins {
    id("codehunters.java-library")
}

version = "3.0.0"
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
    compileOnly("tools.jackson.core:jackson-databind")
    compileOnly("io.micrometer:micrometer-core")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.springframework.boot:spring-boot-jdbc")
    testImplementation("org.springframework.boot:spring-boot-jackson")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-mssqlserver")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.microsoft.sqlserver:mssql-jdbc")
}
