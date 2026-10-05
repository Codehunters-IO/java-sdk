version = "0.2.2"
description = "X-Locale request resolution, MessageSource and localized validation messages."

dependencies {
    compileOnly("org.springframework.boot:spring-boot-webmvc")
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("org.springframework:spring-context")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("org.slf4j:slf4j-api")

    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-webmvc")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework:spring-webmvc")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.mockito:mockito-core")
}
