plugins {
    id("codehunters.java-library")
}

version = "0.2.0"
description = "Raffle purchase geo validation backed by a SQL Server stored procedure."

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-jdbc")

    testImplementation("org.springframework:spring-jdbc")
}
