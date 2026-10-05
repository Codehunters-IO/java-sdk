plugins {
    id("codehunters.java-library")
}

version = "1.2.1"
description = "Typed read access to the shared parameters table over JDBC."

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework:spring-context")
    compileOnly("org.springframework.boot:spring-boot-starter-jdbc")
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.mockito:mockito-core")
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mssqlserver")
    testImplementation("com.microsoft.sqlserver:mssql-jdbc")
}
