plugins {
    id("common")
    application
}

dependencies {
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    implementation(libs.bundles.ktor.server)
    implementation(libs.rapids.and.rivers)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.postgres)
    implementation(project(path = ":modell"))
    implementation(project(path = ":openapi"))
    implementation(project(path = ":aktivitetslogg"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:${libs.versions.ktor.get()}")
    testImplementation("io.kotest:kotest-assertions-core-jvm:${libs.versions.kotest.get()}")
    testImplementation("io.kotest:kotest-assertions-json-jvm:${libs.versions.kotest.get()}")
    testImplementation(libs.mockk)
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.bundles.postgres.test)
    testImplementation("in.specmatic:junit5-support:0.67.0")
}

application {
    mainClass.set("no.nav.dagpenger.rapportering.AppKt")
}
