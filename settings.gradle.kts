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
