package dev.zentro.shared

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Leitura da resposta de `GET /repos/{repo}/releases/latest` da API do GitHub. */
object GitHub {
    fun latestReleaseUrl(repo: String) = "https://api.github.com/repos/$repo/releases/latest"

    fun parseRelease(body: JsonObject, repo: String): UpdateInfo? {
        fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val tag = body.str("tag_name") ?: return null
        val assets = body["assets"]?.jsonArray.orEmpty().mapNotNull { element ->
            val asset = element.jsonObject
            val name = asset.str("name") ?: return@mapNotNull null
            val url = asset.str("browser_download_url") ?: return@mapNotNull null
            name to url
        }.toMap()
        return UpdateInfo(
            version = tag.removePrefix("v"),
            pageUrl = body.str("html_url") ?: "https://github.com/$repo/releases",
            notes = body.str("body").orEmpty().take(4000),
            publishedAt = body.str("published_at").orEmpty(),
            assets = assets,
        )
    }
}
