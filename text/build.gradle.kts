import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

repositories { mavenCentral() }

kotlin {
    jvm()
    js { browser(); nodejs() }
    linuxX64(); macosX64(); macosArm64(); mingwX64()
    @OptIn(ExperimentalWasmDsl::class) wasmJs { nodejs() }
    @OptIn(ExperimentalWasmDsl::class) wasmWasi { nodejs() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

publishing {
    publications.withType<MavenPublication> {
        groupId = "com.dewildte.parsek"
        artifactId = "parsek-text"
        version = "0.1.0-SNAPSHOT"
    }
}
