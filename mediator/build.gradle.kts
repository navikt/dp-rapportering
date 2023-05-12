plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    application
}

repositories {
    mavenCentral()
    maven("https://www.jitpack.io")
}

dependencies {
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    implementation(libs.bundles.ktor.server)
    implementation(libs.rapids.and.rivers)
    implementation(project(mapOf("path" to ":modell")))
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:2.3.0")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.6.1")
    testImplementation("io.kotest:kotest-assertions-json-jvm:5.6.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("no.nav.dagpenger.rapportering.AppKt")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
