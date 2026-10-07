plugins {
    id("codehunters.java-library")
}

version = "3.0.0"
description = "@Measured annotation that records Micrometer timers and counters."

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("io.micrometer:micrometer-core")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-aop")
    implementation("org.springframework:spring-expression")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.aspectj:aspectjrt")
    runtimeOnly("org.aspectj:aspectjweaver")

    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-micrometer-metrics")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("ch.qos.logback:logback-classic")
}
