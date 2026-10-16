package dev.zentro.server

import dev.zentro.shared.LiveMessage
import dev.zentro.shared.Schedule
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Mantém os WebSockets abertos de cada usuário. Quando algo muda num aparelho,
 * todos os outros recebem um "sync" na hora e buscam as novidades.
 */
class LiveHub {
    private val sessions = ConcurrentHashMap<Long, MutableSet<DefaultWebSocketServerSession>>()

    fun join(userId: Long, session: DefaultWebSocketServerSession) {
        sessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun leave(userId: Long, session: DefaultWebSocketServerSession) {
        sessions[userId]?.remove(session)
    }

    suspend fun notify(userId: Long, type: String = "sync") {
        val text = Json.encodeToString(LiveMessage(type, Schedule.now()))
        sessions[userId]?.toList()?.forEach { session ->
            runCatching { session.send(Frame.Text(text)) }.onFailure { leave(userId, session) }
        }
    }

    fun connectedCount(): Int = sessions.values.sumOf { it.size }
}
