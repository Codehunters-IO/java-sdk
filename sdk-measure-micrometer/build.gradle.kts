version = "2.1.2"

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
    testImplementation("org.assertj:assertj-core")
}
