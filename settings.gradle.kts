pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.dokka") version "1.9.20"
    }
}

rootProject.name = "parsek"
include(":core")
include(":text")
include(":markdown")
include(":compose-renderer")
include(":benchmark")
