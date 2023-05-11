plugins {
    id("kotlin-conventions")
    application
}


dependencies {
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    implementation(libs.bundles.ktor.server)
    implementation(libs.rapids.and.rivers)
    implementation(project(mapOf("path" to ":modell")))
    testImplementation("io.ktor:ktor-server-test-host-jvm:2.3.0")
}

application {
    mainClass.set("no.nav.dagpenger.rapportering.AppKt")
}

