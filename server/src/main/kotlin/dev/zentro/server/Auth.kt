package dev.zentro.server

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import java.util.Date

data class UserPrincipal(val id: Long)

class Auth(secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(ISSUER).build()

    /** Sessões longas: é um app pessoal, sair e entrar toda semana só atrapalha. */
    fun issue(userId: Long): String = JWT.create()
        .withIssuer(ISSUER)
        .withClaim(CLAIM_USER, userId)
        .withIssuedAt(Date())
        .withExpiresAt(Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000))
        .sign(algorithm)

    fun hash(password: String): String = BCrypt.withDefaults().hashToString(12, password.toCharArray())

    fun matches(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    companion object {
        const val ISSUER = "zentro"
        const val CLAIM_USER = "uid"
    }
}

val ApplicationCall.userId: Long
    get() = principal<UserPrincipal>()?.id ?: throw ApiException(io.ktor.http.HttpStatusCode.Unauthorized, "Sessão inválida")
