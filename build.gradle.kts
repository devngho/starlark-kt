import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version libs.versions.kotlin
    id("io.kotest") version libs.versions.kotest
    id("com.google.devtools.ksp") version libs.versions.ksp
}


group = "io.github.devngho"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
        d8()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib"))
            implementation(libs.bignum)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)

            implementation(libs.kotlin.test.common)
            implementation(libs.kotlin.test.annotations.common)
            implementation(libs.kotlin.reflect)

            implementation(libs.kotest.runner.junit5)

            implementation(libs.mockk)
        }

        applyDefaultHierarchyTemplate()
    }

    jvmToolchain(25)
}

tasks {
    named<Test>("jvmTest") {
        useJUnitPlatform()
        filter {
            isFailOnNoMatchingTests = false
        }
        testLogging @ExperimentalStdlibApi {
            showExceptions = true
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
