import com.github.davidmc24.gradle.plugin.avro.GenerateAvroProtocolTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.shadow.jar)
    alias(libs.plugins.kotlin)
    id("io.ktor.plugin") version "3.1.3"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "no.nav.dagpenger.rapportering"
version = "0.0.1"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("no.nav.dagpenger.rapportering.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

tasks {
    compileTestKotlin {
        dependsOn(generateTestAvroJava)
    }

    test {
        useJUnitPlatform()
    }

    withType<ShadowJar> {
        mergeServiceFiles()
    }

    named("generateAvroProtocol", GenerateAvroProtocolTask::class.java) {
        source(zipTree(schema.singleFile))
    }
}

ktlint {
    android.set(false)
    outputToConsole.set(true)
    outputColorName.set("RED")
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    maven("https://packages.confluent.io/maven/")
}

val schema by configurations.creating {
    isTransitive = false
}

dependencies {
    implementation(project(path = ":openapi"))

    implementation(libs.rapids.and.rivers)
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation(libs.jackson.annotation)
    implementation("no.nav.dagpenger:oauth2-klient:2025.04.26-14.51.bbf9ece5f5ec")
    implementation("io.ktor:ktor-server-cio:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-config-yaml:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-metrics-micrometer:${libs.versions.ktor.get()}")
    implementation(libs.bundles.postgres)
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.7.0")

    implementation("io.confluent:kafka-streams-avro-serde:7.9.0")
    implementation("org.apache.avro:avro:1.12.0")
    schema("no.nav.paw.arbeidssokerregisteret.api:bekreftelsesmelding-schema:1.25.03.11.31-1")

    implementation("io.getunleash:unleash-client-java:10.2.2")

    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.rapids.and.rivers.test)
    testImplementation(libs.bundles.postgres.test)
    testImplementation("io.kotest:kotest-assertions-core-jvm:${libs.versions.kotest.get()}")
    testImplementation("io.mockk:mockk:${libs.versions.mockk.get()}")
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation("de.redsix:pdfcompare:1.2.3")
}

tasks.named("generateAvroProtocol", GenerateAvroProtocolTask::class.java) {
    source(zipTree(schema.singleFile))
}
