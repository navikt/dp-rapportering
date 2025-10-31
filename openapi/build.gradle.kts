plugins {
    kotlin("jvm")
    id("org.openapi.generator") version "7.17.0"
    id("org.jlleitschuh.gradle.ktlint")
}

group = "org.example"
version = "0.0.1"

tasks.named("compileKotlin").configure {
    dependsOn("openApiGenerate")
}

tasks.named("runKtlintCheckOverMainSourceSet").configure {
    dependsOn("openApiGenerate")
}

tasks.named("runKtlintFormatOverMainSourceSet").configure {
    dependsOn("openApiGenerate")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jackson.annotation)
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/kotlin", "${layout.buildDirectory.get()}/generated/src/main/kotlin"))
        }
    }
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    filter {
        exclude { element -> element.file.path.contains("generated") }
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/src/main/resources/rapportering-api.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/")
    packageName.set("no.nav.dagpenger.rapportering.api")
    globalProperties.set(
        mapOf(
            "apis" to "none",
            "models" to "",
        ),
    )
    modelNameSuffix.set("Response")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "jackson",
            "enumPropertyNaming" to "original",
        ),
    )
}
