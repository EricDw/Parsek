plugins {
    kotlin("multiplatform") version "2.1.0" apply false
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.compose") version "1.8.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("me.champeau.jmh") version "0.7.2" apply false
}

group = "com.dewildte.parsek"
version = "0.1.0"

repositories { mavenCentral() }
