plugins {
    `java-platform`
    id("codehunters.publishing")
}

version = "1.0.0-SNAPSHOT"
description = "Bill of materials aligning the versions of every io.codehunters SDK module."

dependencies {
    constraints {
        api(project(":sdk-audit-log"))
        api(project(":sdk-event-messaging"))
        api(project(":sdk-file-share"))
        api(project(":sdk-geo-validation"))
        api(project(":sdk-i18n"))
        api(project(":sdk-measure-micrometer"))
        api(project(":sdk-parameter"))
        api(project(":sdk-tracing"))
        api(project(":sdk-user-security"))
        api(project(":sdk-user-security-authz"))
        api(project(":sdk-utils"))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            from(components["javaPlatform"])
        }
    }
}
