# Modelos serializados e código do app (pequeno; manter tudo evita surpresas com reflexão)
-keep class dev.zentro.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Ktor descobre o engine HTTP via ServiceLoader
-keep class io.ktor.** { *; }
-keep class * implements io.ktor.client.HttpClientEngineContainer
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
