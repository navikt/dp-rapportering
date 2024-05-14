plugins {
    kotlin("jvm")
    id("org.openapi.generator") version "7.5.0"
}

group = "org.example"
version = "0.0.1"

tasks.named("compileKotlin").configure {
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

openApiGenerate {
    generatorName.set("kotlin-server")
    inputSpec.set("$projectDir/src/main/resources/rapportering-api.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/")
    packageName.set("no.nav.dagpenger.behandling.api")
    globalProperties.set(
        mapOf(
            "apis" to "none",
            "models" to "",
        ),
    )
    modelNameSuffix.set("DTO")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "jackson",
            "enumPropertyNaming" to "original",
        ),
    )
}
