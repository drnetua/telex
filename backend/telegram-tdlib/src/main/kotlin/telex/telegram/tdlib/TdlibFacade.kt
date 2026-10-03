package telex.telegram.tdlib

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Kotlin facade over TDLib, reached only from the `telegram` module's adapter.
 *
 * Only `telex.telegram.tdlib` code sees the TDLight binding (`it.tdlight.*`); everything here is our own types
 * (ADR-0004). The surface is the minimum the adapter needs: open a client for a session directory, send a request,
 * receive updates on a callback, close.
 */
interface TdlibFacade {
    /** Starts a TDLib client; [listener] receives every update until the client reports [TdlibUpdate.Closed]. */
    fun open(
        config: TdlibClientConfig,
        listener: TdlibUpdateListener,
    ): TdlibClient
}

/** Everything TDLib needs to open one Linked Account's session. */
class TdlibClientConfig(
    /** Directory holding this account's TDLib database and files; never shared between clients. */
    val sessionDirectory: Path,
    /** The 32-byte key encrypting the TDLib database (envelope-encrypted per Owner elsewhere). */
    val databaseKey: ByteArray,
    val apiId: Int,
    val apiHash: String,
    /** True to talk to Telegram's test servers. */
    val useTestDc: Boolean = false,
) {
    init {
        require(databaseKey.size == DATABASE_KEY_BYTES) { "databaseKey must be $DATABASE_KEY_BYTES bytes" }
    }

    // Never print the key or the api hash.
    override fun toString() =
        "TdlibClientConfig(sessionDirectory=$sessionDirectory, apiId=$apiId, useTestDc=$useTestDc)"

    companion object {
        const val DATABASE_KEY_BYTES = 32
    }
}

fun interface TdlibUpdateListener {
    fun onUpdate(update: TdlibUpdate)
}

/** One running TDLib client. [close] asks TDLib to shut down; completion is reported as [TdlibUpdate.Closed]. */
interface TdlibClient : AutoCloseable {
    fun send(request: TdlibRequest): CompletableFuture<TdlibResponse>

    override fun close()
}

/** Requests the facade can send; grows with the adapter (T6). */
sealed interface TdlibRequest {
    data object GetAuthorizationState : TdlibRequest
}

sealed interface TdlibResponse {
    /** TDLib answered with an object, named by its TL type. */
    data class Ok(
        val type: String,
    ) : TdlibResponse

    data class Failure(
        val code: Int,
        val message: String,
    ) : TdlibResponse
}

sealed interface TdlibUpdate {
    /** TDLib's authorization state, by its TL name, e.g. `authorizationStateWaitPhoneNumber`. */
    data class AuthorizationState(
        val state: String,
    ) : TdlibUpdate

    /** Any update the facade does not model yet, by its TL name. */
    data class Other(
        val type: String,
    ) : TdlibUpdate

    /** TDLib finished closing; the client is dead. */
    data object Closed : TdlibUpdate

    /** The binding reported a failure outside a request. */
    data class Failed(
        val cause: Throwable,
    ) : TdlibUpdate
}
