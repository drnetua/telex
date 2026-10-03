package telex.telegram.tdlib

import it.tdlight.ClientFactory
import it.tdlight.ExceptionHandler
import it.tdlight.Init
import it.tdlight.Log
import it.tdlight.ResultHandler
import it.tdlight.TelegramClient
import it.tdlight.jni.TdApi
import java.nio.file.Files
import java.util.concurrent.CompletableFuture

/**
 * [TdlibFacade] over TDLight Java. The only class family that imports `it.tdlight.*` (ADR-0004).
 *
 * Handles TDLib's `authorizationStateWaitTdlibParameters` itself, so a caller sees the session start at
 * `authorizationStateWaitPhoneNumber` (or `authorizationStateReady` for a stored session).
 */
class TdlightFacade : TdlibFacade {
    override fun open(
        config: TdlibClientConfig,
        listener: TdlibUpdateListener,
    ): TdlibClient {
        ensureNativesLoaded()
        val database = Files.createDirectories(config.sessionDirectory.resolve("db"))
        val files = Files.createDirectories(config.sessionDirectory.resolve("files"))
        val client = factory.createClient()
        val parameters =
            TdApi.SetTdlibParameters().apply {
                useTestDc = config.useTestDc
                databaseDirectory = database.toString()
                filesDirectory = files.toString()
                databaseEncryptionKey = config.databaseKey.copyOf()
                useFileDatabase = true
                useChatInfoDatabase = true
                useMessageDatabase = true
                useSecretChats = false
                apiId = config.apiId
                apiHash = config.apiHash
                systemLanguageCode = "en"
                deviceModel = "teleX"
                systemVersion = "Server"
                applicationVersion = "1.0"
            }
        client.initialize(
            ResultHandler<TdApi.Update> { update -> dispatch(client, parameters, update, listener) },
            ExceptionHandler { error -> listener.onUpdate(TdlibUpdate.Failed(error)) },
            ExceptionHandler { error -> listener.onUpdate(TdlibUpdate.Failed(error)) },
        )
        return TdlightClient(client)
    }

    private fun dispatch(
        client: TelegramClient,
        parameters: TdApi.SetTdlibParameters,
        update: TdApi.Object,
        listener: TdlibUpdateListener,
    ) {
        if (update !is TdApi.UpdateAuthorizationState) {
            listener.onUpdate(TdlibUpdate.Other(tlName(update)))
            return
        }
        when (val state = update.authorizationState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                client.send(parameters) { result ->
                    if (result is TdApi.Error) {
                        listener.onUpdate(
                            TdlibUpdate.Failed(IllegalStateException("setTdlibParameters: ${result.message}")),
                        )
                    }
                }
            }

            is TdApi.AuthorizationStateClosed -> {
                listener.onUpdate(TdlibUpdate.Closed)
            }

            else -> {
                listener.onUpdate(TdlibUpdate.AuthorizationState(tlName(state)))
            }
        }
    }

    private class TdlightClient(
        private val client: TelegramClient,
    ) : TdlibClient {
        override fun send(request: TdlibRequest): CompletableFuture<TdlibResponse> {
            val result = CompletableFuture<TdlibResponse>()
            val function =
                when (request) {
                    TdlibRequest.GetAuthorizationState -> TdApi.GetAuthorizationState()
                }
            client.send(function, { response -> result.complete(toResponse(response)) }) { error ->
                result.completeExceptionally(error)
            }
            return result
        }

        override fun close() {
            client.send(TdApi.Close()) { /* completion is reported as authorizationStateClosed */ }
        }

        private fun toResponse(response: TdApi.Object): TdlibResponse =
            when (response) {
                is TdApi.Error -> TdlibResponse.Failure(response.code, response.message)
                else -> TdlibResponse.Ok(tlName(response))
            }
    }

    private companion object {
        /** TDLib log verbosity: fatal errors only, so nothing from the native side reaches our logs. */
        const val LOG_VERBOSITY = 1

        val factory: ClientFactory by lazy { ClientFactory.acquireCommonClientFactory() }

        private val nativesLoaded: Unit by lazy {
            Init.init()
            Log.setVerbosityLevel(LOG_VERBOSITY)
        }

        fun ensureNativesLoaded() = nativesLoaded

        /** `TdApi.AuthorizationStateReady` -> `authorizationStateReady`, TDLib's own name for the type. */
        fun tlName(value: TdApi.Object): String = value.javaClass.simpleName.replaceFirstChar { it.lowercaseChar() }
    }
}
