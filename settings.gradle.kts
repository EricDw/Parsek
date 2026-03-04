pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

rootProject.name = "parsek"
include(":core")
include(":text")
include(":markdown")
include(":compose-renderer")
include(":markdown2")
include(":benchmark")
