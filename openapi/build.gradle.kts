plugins {
    id("org.openapi.generator") version "6.6.0"
    id("common")
    `java-library`
}

tasks.named("compileKotlin").configure {
    dependsOn("openApiGenerate")
}

tasks.named("spotlessKotlin").configure {
    dependsOn("openApiGenerate")
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/kotlin", "$buildDir/generated/src/main/"))
        }
    }
}

spotless {
    kotlin {
        targetExclude("**/generated/**")
    }
}

dependencies {
    implementation(libs.jackson.annotation)
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/src/main/resources/rapportering-api.json")
    outputDir.set("$buildDir/generated/src/")
    packageName.set("no.nav.dagpenger.rapportering.api")
    globalProperties.set(
        mapOf(
            "apis" to "none",
            "models" to "",
            "modelDocs" to "false",
        ),
    )
    configOptions.set(
        mapOf(
            "serializationLibrary" to "jackson",
            "enumPropertyNaming" to "UPPERCASE",
        ),
    )
}
