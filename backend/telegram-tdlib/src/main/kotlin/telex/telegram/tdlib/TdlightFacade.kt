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
            listener.onUpdate(mapUpdate(update))
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

            is TdApi.AuthorizationStateWaitCode -> {
                listener.onUpdate(
                    TdlibUpdate.AuthorizationState(tlName(state), codeLength = codeLength(state.codeInfo?.type)),
                )
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                listener.onUpdate(
                    TdlibUpdate.AuthorizationState(tlName(state), passwordHint = state.passwordHint?.ifEmpty { null }),
                )
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
            if (request is TdlibRequest.GetCallingCode) {
                result.complete(callingCode(request))
                return result
            }
            client.send(toFunction(request), { response -> result.complete(toResponse(request, response)) }) { error ->
                result.completeExceptionally(error)
            }
            return result
        }

        private fun callingCode(request: TdlibRequest.GetCallingCode): TdlibResponse =
            when (val info = client.execute(TdApi.GetPhoneNumberInfoSync("en", request.phoneNumber))) {
                is TdApi.PhoneNumberInfo -> TdlibResponse.CallingCode(info.countryCallingCode)
                is TdApi.Error -> TdlibResponse.Failure(info.code, info.message)
                else -> TdlibResponse.Ok(tlName(info))
            }

        private fun toFunction(request: TdlibRequest): TdApi.Function<*> =
            when (request) {
                TdlibRequest.GetAuthorizationState -> TdApi.GetAuthorizationState()
                is TdlibRequest.SetPhoneNumber -> TdApi.SetAuthenticationPhoneNumber(request.phoneNumber, null)
                TdlibRequest.ResendCode -> TdApi.ResendAuthenticationCode()
                is TdlibRequest.CheckCode -> TdApi.CheckAuthenticationCode(request.code)
                is TdlibRequest.CheckPassword -> TdApi.CheckAuthenticationPassword(request.password)
                TdlibRequest.GetMe -> TdApi.GetMe()
                is TdlibRequest.LoadChats -> TdApi.LoadChats(toChatList(request.list), request.limit)
                TdlibRequest.LogOut -> TdApi.LogOut()
                is TdlibRequest.GetCallingCode -> error("handled by send")
            }

        override fun close() {
            client.send(TdApi.Close()) { /* completion is reported as authorizationStateClosed */ }
        }

        private fun toResponse(
            request: TdlibRequest,
            response: TdApi.Object,
        ): TdlibResponse =
            when {
                response is TdApi.Error -> TdlibResponse.Failure(response.code, response.message)
                request == TdlibRequest.GetMe && response is TdApi.User -> me(response)
                else -> TdlibResponse.Ok(tlName(response))
            }

        private fun me(user: TdApi.User) =
            TdlibResponse.Me(user.id, user.firstName.orEmpty(), user.lastName.orEmpty(), user.phoneNumber.orEmpty())
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

        const val DEFAULT_CODE_LENGTH = 5

        fun codeLength(type: TdApi.AuthenticationCodeType?): Int =
            when (type) {
                is TdApi.AuthenticationCodeTypeTelegramMessage -> type.length
                is TdApi.AuthenticationCodeTypeSms -> type.length
                is TdApi.AuthenticationCodeTypeCall -> type.length
                else -> DEFAULT_CODE_LENGTH
            }

        fun toChatList(list: TdlibChatList): TdApi.ChatList =
            when (list) {
                TdlibChatList.Main -> TdApi.ChatListMain()
                TdlibChatList.Archive -> TdApi.ChatListArchive()
                is TdlibChatList.Folder -> TdApi.ChatListFolder(list.id)
            }

        fun fromChatList(list: TdApi.ChatList?): TdlibChatList =
            when (list) {
                is TdApi.ChatListArchive -> TdlibChatList.Archive
                is TdApi.ChatListFolder -> TdlibChatList.Folder(list.chatFolderId)
                else -> TdlibChatList.Main
            }

        fun position(position: TdApi.ChatPosition) = TdlibChatPosition(fromChatList(position.list), position.order)

        fun positions(positions: Array<TdApi.ChatPosition>?) = positions.orEmpty().map(::position)

        fun chatType(type: TdApi.ChatType?): TdlibChatType =
            when (type) {
                is TdApi.ChatTypeSecret -> TdlibChatType.Secret
                is TdApi.ChatTypeBasicGroup -> TdlibChatType.BasicGroup
                is TdApi.ChatTypeSupergroup -> if (type.isChannel) TdlibChatType.Channel else TdlibChatType.Supergroup
                else -> TdlibChatType.Private
            }

        fun mapUpdate(update: TdApi.Object): TdlibUpdate =
            when (update) {
                is TdApi.UpdateConnectionState -> {
                    TdlibUpdate.ConnectionState(tlName(update.state))
                }

                is TdApi.UpdateNewChat -> {
                    TdlibUpdate.NewChat(chat(update.chat))
                }

                is TdApi.UpdateChatTitle -> {
                    TdlibUpdate.ChatTitle(update.chatId, update.title)
                }

                is TdApi.UpdateChatPosition -> {
                    TdlibUpdate.ChatPosition(update.chatId, position(update.position))
                }

                is TdApi.UpdateChatLastMessage -> {
                    TdlibUpdate.ChatPositions(update.chatId, positions(update.positions))
                }

                is TdApi.UpdateChatReadInbox -> {
                    TdlibUpdate.ChatUnread(update.chatId, update.unreadCount)
                }

                is TdApi.UpdateChatRemovedFromList -> {
                    TdlibUpdate.ChatPosition(update.chatId, TdlibChatPosition(fromChatList(update.chatList), 0))
                }

                is TdApi.UpdateUnreadChatCount -> {
                    TdlibUpdate.ChatCount(fromChatList(update.chatList), update.totalCount)
                }

                else -> {
                    TdlibUpdate.Other(tlName(update))
                }
            }

        fun chat(chat: TdApi.Chat) =
            TdlibChat(chat.id, chatType(chat.type), chat.title.orEmpty(), chat.unreadCount, positions(chat.positions))
    }
}
