rootProject.name = "zentro"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":shared")
include(":server")

// O app (Android + Desktop) precisa do Android SDK. Para compilar apenas o servidor
// numa máquina sem SDK, use: ./gradlew -Pzentro.serverOnly=true :server:shadowJar
if (providers.gradleProperty("zentro.serverOnly").orNull != "true") {
    include(":composeApp")
}
