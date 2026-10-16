package dev.zentro.server

import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * Configuração lida de variáveis de ambiente — é assim que o egg do Pterodactyl
 * repassa as opções do painel para o servidor.
 */
data class Config(
    val port: Int,
    val dataDir: File,
    val jwtSecret: String,
    val allowRegistration: Boolean,
    val telegramToken: String?,
    val publicUrl: String?,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            fun value(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }

            val dataDir = File(value("ZENTRO_DATA_DIR") ?: "data").absoluteFile.apply { mkdirs() }
            return Config(
                // O Pterodactyl injeta SERVER_PORT com a porta alocada para o servidor.
                port = (value("SERVER_PORT") ?: value("PORT") ?: "8080").toInt(),
                dataDir = dataDir,
                jwtSecret = value("ZENTRO_JWT_SECRET") ?: persistentSecret(dataDir),
                allowRegistration = value("ZENTRO_ALLOW_REGISTRATION")?.lowercase() !in setOf("false", "0", "no", "nao", "não"),
                telegramToken = value("TELEGRAM_BOT_TOKEN"),
                publicUrl = value("ZENTRO_PUBLIC_URL")?.trimEnd('/'),
            )
        }

        /** Gera uma chave na primeira execução e a reutiliza depois, para as sessões sobreviverem a reinícios. */
        private fun persistentSecret(dataDir: File): String {
            val file = File(dataDir, "jwt.secret")
            if (file.exists()) return file.readText().trim()
            val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
            return Base64.getEncoder().encodeToString(bytes).also { file.writeText(it) }
        }
    }
}
