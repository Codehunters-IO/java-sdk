pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "java-sdk"

// Library modules (see the README module table).
include("sdk-measure-micrometer")
include("sdk-geo-validation")
include("sdk-tracing")
include("sdk-parameter")
include("sdk-audit-log")
include("sdk-user-security")
include("sdk-user-security-authz")
include("sdk-file-share")
include("sdk-event-messaging-core")
include("sdk-event-messaging-jdbc")
include("sdk-event-messaging-rabbit")
include("sdk-event-messaging-kafka")
include("sdk-commons")
include("sdk-web")
include("sdk-barcode")
include("sdk-jasper")
include("sdk-jpa")
include("sdk-bom")

// Module families live in grouped directories; project and artifact names stay `sdk-*`.
project(":sdk-event-messaging-core").projectDir = file("messaging/sdk-event-messaging-core")
project(":sdk-event-messaging-jdbc").projectDir = file("messaging/sdk-event-messaging-jdbc")
project(":sdk-event-messaging-rabbit").projectDir = file("messaging/sdk-event-messaging-rabbit")
project(":sdk-event-messaging-kafka").projectDir = file("messaging/sdk-event-messaging-kafka")
project(":sdk-user-security").projectDir = file("security/sdk-user-security")
project(":sdk-user-security-authz").projectDir = file("security/sdk-user-security-authz")
project(":sdk-bom").projectDir = file("platform/sdk-bom")
