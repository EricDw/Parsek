import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.1.0"
    `maven-publish`
}

group = "com.dewildte.parsek"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js { browser(); nodejs() }
    linuxX64(); macosX64(); macosArm64(); mingwX64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { nodejs() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi { nodejs() }

    sourceSets {
        commonMain.dependencies { /* pure Kotlin, no extra deps */ }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        groupId = "com.dewildte.parsek"
        artifactId = "parsek"
        version = "0.1.0-SNAPSHOT"
    }
}
