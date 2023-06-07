plugins {
    id("common")
    `java-library`
}

dependencies {
    implementation(project(":aktivitetslogg"))
    testImplementation(libs.bundles.kotest.assertions)
    testImplementation(project(path = ":common-test"))
}
