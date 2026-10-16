plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

val androidEnabled: Boolean by rootProject.extra
if (androidEnabled) apply(plugin = "com.android.library")

kotlin {
    jvmToolchain(21)
    jvm()
    if (androidEnabled) androidTarget()

    sourceSets {
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
