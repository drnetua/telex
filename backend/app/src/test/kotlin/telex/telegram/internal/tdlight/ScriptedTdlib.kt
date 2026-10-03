package telex.telegram.internal.tdlight

import telex.telegram.tdlib.TdlibClient
import telex.telegram.tdlib.TdlibClientConfig
import telex.telegram.tdlib.TdlibFacade
import telex.telegram.tdlib.TdlibRequest
import telex.telegram.tdlib.TdlibResponse
import telex.telegram.tdlib.TdlibUpdate
import telex.telegram.tdlib.TdlibUpdateListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/** A [TdlibFacade] whose clients answer requests through [respond] and emit updates when the test says so. */
class ScriptedTdlib(
    /** Called on the requesting thread; may call [Client.emit] before returning the answer. Null never answers. */
    var respond: (Client, TdlibRequest) -> TdlibResponse? = { _, _ -> TdlibResponse.Ok("ok") },
    /** Updates emitted as soon as a client opens (what TDLib reports on its own). */
    var onOpen: (Client) -> Unit = { it.emit(TdlibUpdate.AuthorizationState("authorizationStateWaitPhoneNumber")) },
) : TdlibFacade {
    /** When set, [open] throws it before any client exists. */
    @Volatile var openFailure: RuntimeException? = null
    val clients = CopyOnWriteArrayList<Client>()

    inner class Client(
        val config: TdlibClientConfig,
        private val listener: TdlibUpdateListener,
    ) : TdlibClient {
        val requests = CopyOnWriteArrayList<TdlibRequest>()

        @Volatile var closeCalled = false

        fun emit(update: TdlibUpdate) = listener.onUpdate(update)

        override fun send(request: TdlibRequest): CompletableFuture<TdlibResponse> {
            requests += request
            val answer = respond(this, request)
            return if (answer == null) CompletableFuture() else CompletableFuture.completedFuture(answer)
        }

        override fun close() {
            closeCalled = true
            emit(TdlibUpdate.Closed)
        }
    }

    override fun open(
        config: TdlibClientConfig,
        listener: TdlibUpdateListener,
    ): TdlibClient {
        openFailure?.let { throw it }
        val client = Client(config, listener)
        clients += client
        onOpen(client)
        return client
    }
}
