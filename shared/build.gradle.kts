plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

val androidEnabled: Boolean by rootProject.extra
if (androidEnabled) apply(plugin = "com.android.library")

// Gera BuildVersion.kt a partir do catálogo: uma única fonte da versão para servidor e apps.
val generateVersion by tasks.registering {
    val version = libs.versions.zentro.get()
    val outDir = layout.buildDirectory.dir("generated/version/kotlin")
    inputs.property("version", version)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("dev/zentro/shared/BuildVersion.kt").asFile
        file.parentFile.mkdirs()
        val q = '"'
        file.writeText(
            listOf("package dev.zentro.shared", "", "object BuildVersion {", "    const val CURRENT = $q$version$q", "}", "")
                .joinToString("\n"),
        )
    }
}

kotlin {
    jvmToolchain(21)
    jvm()
    if (androidEnabled) androidTarget()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateVersion)
        }
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

if (androidEnabled) {
    extensions.configure<com.android.build.gradle.LibraryExtension> {
        namespace = "dev.zentro.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        defaultConfig.minSdk = libs.versions.android.minSdk.get().toInt()
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
