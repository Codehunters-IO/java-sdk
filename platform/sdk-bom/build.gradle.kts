plugins {
    id("codehunters.bom")
}

version = "1.2.0"
description = "Bill of materials aligning the versions of every io.codehunters SDK module."

// Every released library, and nothing else: `verifyBomConstraints` (part of `check`) fails when a
// module is missing, or a SNAPSHOT or excluded one is listed. `verifyBomLock` fails when a library
// version changes while this BOM's version doesn't; bump it and run `:sdk-bom:updateBomLock`.
dependencies {
    constraints {
        api(project(":sdk-audit-log"))
        api(project(":sdk-barcode"))
        api(project(":sdk-commons"))
        api(project(":sdk-event-messaging-core"))
        api(project(":sdk-event-messaging-jdbc"))
        api(project(":sdk-event-messaging-kafka"))
        api(project(":sdk-event-messaging-rabbit"))
        api(project(":sdk-file-share"))
        api(project(":sdk-geo-validation"))
        api(project(":sdk-jasper"))
        api(project(":sdk-jpa"))
        api(project(":sdk-measure-micrometer"))
        api(project(":sdk-parameter"))
        api(project(":sdk-support"))
        api(project(":sdk-tracing"))
        api(project(":sdk-user-security"))
        api(project(":sdk-user-security-authz"))
        api(project(":sdk-web"))
    }
}
