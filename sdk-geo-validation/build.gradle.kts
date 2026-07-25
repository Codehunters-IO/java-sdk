version = "0.2.0"

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-jdbc")

    testImplementation("org.springframework:spring-jdbc")
}
