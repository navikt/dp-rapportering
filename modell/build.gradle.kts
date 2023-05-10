plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    `java-library`
}

repositories {
    mavenCentral()
    maven("https://www.jitpack.io")
}

dependencies {
    implementation(project(":aktivitetslogg"))
    testImplementation(kotlin("test"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
