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
include("sdk-file-share")
include("sdk-event-messaging")
include("sdk-bom")

// Module families live in grouped directories; project and artifact names stay `sdk-*`.
project(":sdk-event-messaging").projectDir = file("messaging/sdk-event-messaging")
project(":sdk-user-security").projectDir = file("security/sdk-user-security")
project(":sdk-bom").projectDir = file("platform/sdk-bom")
