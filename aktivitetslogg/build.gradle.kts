plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.21"
    `java-library`
}

repositories {
    mavenCentral()
    maven("https://www.jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
