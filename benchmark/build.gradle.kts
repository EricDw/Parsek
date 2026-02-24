plugins {
    kotlin("jvm")
    id("me.champeau.jmh")
    application
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":core"))
    implementation(project(":text"))
    implementation(project(":commonmark"))
}

application {
    mainClass.set("parsek.benchmark.ProfileRunnerKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    resultFormat.set("TEXT")
}
