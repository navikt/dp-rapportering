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
    implementation(libs.rapids.and.rivers)
    implementation(project(mapOf("path" to ":modell")))
    testImplementation(kotlin("test"))
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
