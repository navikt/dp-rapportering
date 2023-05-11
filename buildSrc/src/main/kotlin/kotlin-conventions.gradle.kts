import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
}


repositories {
    mavenCentral()
    maven("https://www.jitpack.io")
}

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib-jdk8"))

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.6.1")
    testImplementation("io.kotest:kotest-assertions-json-jvm:5.6.1")

}
kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

//spotless {
//    kotlin {
//        ktlint("0.40.0")
//    }
//    kotlinGradle {
//        target("*.gradle.kts")
//        ktlint("0.40.0")
//    }
//}




