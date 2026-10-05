pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "java-sdk"

// Modules are included here as each is migrated from vitxo-sdk-* (see README module table).
include("sdk-utils")
include("sdk-measure-micrometer")
include("sdk-geo-validation")
include("sdk-i18n")
include("sdk-tracing")
include("sdk-parameter")
include("sdk-audit-log")
include("sdk-user-security")
include("sdk-user-security-authz")
include("sdk-file-share")
include("sdk-event-messaging")
include("sdk-support")
include("sdk-bom")

// Module families live in grouped directories; project and artifact names stay `sdk-*`.
project(":sdk-event-messaging").projectDir = file("messaging/sdk-event-messaging")
project(":sdk-user-security").projectDir = file("security/sdk-user-security")
project(":sdk-user-security-authz").projectDir = file("security/sdk-user-security-authz")
project(":sdk-support").projectDir = file("platform/sdk-support")
project(":sdk-bom").projectDir = file("platform/sdk-bom")
