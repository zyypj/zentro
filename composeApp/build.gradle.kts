import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

val androidEnabled: Boolean by rootProject.extra
if (androidEnabled) apply(plugin = "com.android.application")

kotlin {
    jvmToolchain(21)
    jvm("desktop")
    if (androidEnabled) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }

    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "kotlin.uuid.ExperimentalUuidApi",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.compose.material.icons.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.json)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.cio)
            }
        }
        if (androidEnabled) {
            androidMain.dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.work)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}

if (androidEnabled) {
    extensions.configure<com.android.build.gradle.internal.dsl.BaseAppModuleExtension> {
        namespace = "dev.zentro.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        defaultConfig {
            applicationId = "dev.zentro.app"
            minSdk = libs.versions.android.minSdk.get().toInt()
            targetSdk = libs.versions.android.targetSdk.get().toInt()
            versionCode = 1
            versionName = libs.versions.zentro.get()
        }
        buildTypes {
            getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                // Assina com a chave de debug para gerar um APK instalável direto; troque por uma chave própria para publicar na Play Store.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        packaging {
            resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/io.netty.versions.properties")
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.zentro.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Zentro"
            packageVersion = libs.versions.zentro.get()
            description = "Agenda e finanças pessoais"
            vendor = "Zentro"
            modules("java.naming", "jdk.unsupported", "jdk.crypto.ec", "java.net.http")
            windows {
                menu = true
                shortcut = true
                perUserInstall = true
                dirChooser = false
                upgradeUuid = "8f0f5a52-6a3b-4b8e-9a51-2f7f7c1d9e10"
                iconFile.set(project.file("icons/zentro.ico"))
            }
            linux {
                iconFile.set(project.file("icons/zentro.png"))
            }
            macOS {
                bundleID = "dev.zentro.app"
                iconFile.set(project.file("icons/zentro.icns"))
            }
        }
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}
