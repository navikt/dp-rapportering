plugins {
    id("common")
    `java-library`
}

dependencies {
    api(libs.dp.aktivitetslogg)

    testImplementation(libs.bundles.kotest.assertions)
    testApi("org.junit.platform:junit-platform-suite-api:1.10.2")
    testImplementation("org.junit.platform:junit-platform-suite-engine:1.10.2")
    testImplementation("io.cucumber:cucumber-java8:7.15.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.15.0")
    testImplementation(project(path = ":common-test"))
}
