package telex.telegram.tdlib

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class TdlightFacadeTest {
    @TempDir
    lateinit var tmp: Path

    private class Session(
        val client: TdlibClient,
        val waitPhone: CompletableFuture<Unit>,
        val closed: CompletableFuture<Unit>,
    )

    private fun open(
        facade: TdlibFacade,
        name: String,
    ): Session {
        val waitPhone = CompletableFuture<Unit>()
        val closed = CompletableFuture<Unit>()
        val key = ByteArray(TdlibClientConfig.DATABASE_KEY_BYTES) { 7 }
        val config = TdlibClientConfig(tmp.resolve(name), key, apiId = 1, apiHash = "hash", useTestDc = true)
        val client =
            facade.open(config) { update ->
                when (update) {
                    TdlibUpdate.AuthorizationState("authorizationStateWaitPhoneNumber") -> {
                        waitPhone.complete(Unit)
                    }

                    TdlibUpdate.Closed -> {
                        closed.complete(Unit)
                    }

                    is TdlibUpdate.Failed -> {
                        waitPhone.completeExceptionally(update.cause)
                        closed.completeExceptionally(update.cause)
                    }

                    else -> {
                        Unit
                    }
                }
            }
        return Session(client, waitPhone, closed)
    }

    @Test
    fun `natives load and two clients start and close in one JVM without network`() {
        val facade = TdlightFacade()
        val sessions = listOf(open(facade, "a"), open(facade, "b"))

        sessions.forEach { it.waitPhone.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        val state =
            sessions
                .first()
                .client
                .send(TdlibRequest.GetAuthorizationState)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals(TdlibResponse.Ok("authorizationStateWaitPhoneNumber"), state)

        sessions.forEach { it.client.close() }
        sessions.forEach { it.closed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
    }
}
