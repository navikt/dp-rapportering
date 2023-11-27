plugins {
    id("common")
    `java-library`
}

dependencies {
    api(libs.dp.aktivitetslogg)

    testImplementation(libs.bundles.kotest.assertions)
    testApi("org.junit.platform:junit-platform-suite-api:1.10.1")
    testImplementation("org.junit.platform:junit-platform-suite-engine:1.10.1")
    testImplementation("io.cucumber:cucumber-java8:7.14.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.14.1")
    testImplementation(project(path = ":common-test"))
}
