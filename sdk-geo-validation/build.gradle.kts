plugins {
    id("codehunters.java-library")
}

version = "1.0.0"
description = "Geofencing: decides whether a point falls inside the zones of a caller-defined scope."

dependencies {
    implementation(project(":sdk-support"))
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-jdbc")

    testImplementation("org.springframework:spring-jdbc")
    testImplementation(libs.archunit.junit5)
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mssqlserver")
    testRuntimeOnly("com.microsoft.sqlserver:mssql-jdbc")
}
