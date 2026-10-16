plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.shadow) apply false
}

val zentroVersion = libs.versions.zentro.get()

// O alvo Android só é configurado quando existe um Android SDK na máquina.
// Assim o servidor e o app desktop compilam em qualquer lugar (inclusive no Pterodactyl/CI sem SDK).
val androidEnabled: Boolean = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
extra["androidEnabled"] = androidEnabled

allprojects {
    group = "dev.zentro"
    version = zentroVersion
}
