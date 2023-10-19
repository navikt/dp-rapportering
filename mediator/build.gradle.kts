plugins {
    id("common")
    application
}

dependencies {
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    implementation(libs.bundles.ktor.server)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.rapids.and.rivers)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.postgres)
    implementation(project(path = ":modell"))
    implementation(project(path = ":openapi"))
    implementation("io.ktor:ktor-server-core-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-host-common-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-status-pages-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-swagger:${libs.versions.ktor.get()}")
    testImplementation("io.ktor:ktor-server-test-host-jvm:${libs.versions.ktor.get()}")
    testImplementation("io.kotest:kotest-assertions-core-jvm:${libs.versions.kotest.get()}")
    testImplementation("io.kotest:kotest-assertions-json-jvm:${libs.versions.kotest.get()}")
    testImplementation(libs.mockk)
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.bundles.postgres.test)
    testImplementation("in.specmatic:junit5-support:0.81.1")
    testImplementation(project(path = ":common-test"))
}

application {
    mainClass.set("no.nav.dagpenger.rapportering.AppKt")
}
