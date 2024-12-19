import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.shadow.jar)
    alias(libs.plugins.kotlin)
    id("io.ktor.plugin") version "3.0.3"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
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

sourceSets {
    main {
        kotlin {
            setSrcDirs(
                listOf(
                    "src/main/kotlin",
                    "${layout.buildDirectory.get()}/generated/src/main/kotlin",
                ),
            )
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    withType<ShadowJar> {
        mergeServiceFiles()
    }
}

ktlint {
    android.set(false)
    outputToConsole.set(true)
    outputColorName.set("RED")
}

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
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
    implementation("no.nav.dagpenger:oauth2-klient:2024.12.10-14.29.b14a663ac6da")
    implementation("io.ktor:ktor-server-cio:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-config-yaml:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-metrics-micrometer:${libs.versions.ktor.get()}")
    implementation(libs.bundles.postgres)
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.6.0")

    testImplementation(libs.rapids.and.rivers.test)
    testImplementation(libs.bundles.postgres.test)
    testImplementation("io.kotest:kotest-assertions-core-jvm:${libs.versions.kotest.get()}")
    testImplementation("io.mockk:mockk:${libs.versions.mockk.get()}")
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.ktor.server.test.host)
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    testImplementation(libs.ktor.client.mock)
    testImplementation("de.redsix:pdfcompare:1.2.3")
}
