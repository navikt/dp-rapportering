plugins {
    id("common")
    application
}

dependencies {
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    implementation(libs.bundles.ktor.server)
    implementation(libs.rapids.and.rivers)
    implementation(project(mapOf("path" to ":modell")))
    testImplementation("io.ktor:ktor-server-test-host-jvm:${libs.versions.ktor.get()}")
    testImplementation("io.kotest:kotest-assertions-core-jvm:${libs.versions.kotest.get()}")
    testImplementation("io.kotest:kotest-assertions-json-jvm:${libs.versions.kotest.get()}")
}

application {
    mainClass.set("no.nav.dagpenger.rapportering.AppKt")
}
