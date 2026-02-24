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
include(":commonmark")
include(":compose-renderer")
include(":benchmark")
