plugins {
    id("codehunters.java-library")
}

version = "1.0.0"
description = "Who is calling: client IP behind trusted proxies, parsed User-Agent, and a geo-IP port."

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-web")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly(libs.uap.java)

    testImplementation("org.springframework:spring-web")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation(libs.uap.java)
    testImplementation(libs.archunit.junit5)
    testImplementation("org.awaitility:awaitility")
}
