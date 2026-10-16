package dev.zentro.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubTest {
    @Test
    fun parsesLatestRelease() {
        val body = Json.parseToJsonElement(
            """
            {"tag_name":"v1.2.0","html_url":"https://github.com/zyypj/zentro/releases/tag/v1.2.0","body":"notas","published_at":"2026-11-01T10:00:00Z",
             "assets":[{"name":"Zentro.apk","browser_download_url":"https://x/Zentro.apk"},{"name":"Zentro-1.2.0.msi","browser_download_url":"https://x/z.msi"}]}
            """,
        ) as JsonObject
        val info = GitHub.parseRelease(body, "zyypj/zentro")!!
        assertEquals("1.2.0", info.version)
        assertEquals("https://x/Zentro.apk", info.asset(".apk"))
        assertEquals("https://x/z.msi", info.asset(".msi"))
        assertNull(info.asset(".dmg"))
    }

    @Test
    fun ignoresResponsesWithoutTag() {
        assertNull(GitHub.parseRelease(Json.parseToJsonElement("""{"message":"Not Found"}""") as JsonObject, "x/y"))
    }
}
