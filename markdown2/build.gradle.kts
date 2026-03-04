import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

repositories { mavenCentral() }

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            testLogging.showStandardStreams = true
        }
    }
    js { browser(); nodejs() }
    linuxX64(); macosX64(); macosArm64(); mingwX64()
    @OptIn(ExperimentalWasmDsl::class) wasmJs { nodejs() }
    @OptIn(ExperimentalWasmDsl::class) wasmWasi { nodejs() }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(project(":text"))
            api(project(":markdown"))
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

publishing {
    publications.withType<MavenPublication> {
        groupId = "com.dewildte.parsek"
        artifactId = "parsek-$artifactId"
        version = "0.1.0"
    }
}
