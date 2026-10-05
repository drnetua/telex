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

/** Which chat list a request or a position refers to. */
sealed interface TdlibChatList {
    data object Main : TdlibChatList

    data object Archive : TdlibChatList

    data class Folder(
        val id: Int,
    ) : TdlibChatList
}

/**
 * Requests the facade can send. Requests carrying a phone number, code or password redact them in [toString]
 * (they are never logged).
 */
sealed interface TdlibRequest {
    data object GetAuthorizationState : TdlibRequest

    class SetPhoneNumber(
        val phoneNumber: String,
    ) : TdlibRequest {
        override fun toString() = "SetPhoneNumber"
    }

    data object ResendCode : TdlibRequest

    class CheckCode(
        val code: String,
    ) : TdlibRequest {
        override fun toString() = "CheckCode"
    }

    class CheckPassword(
        val password: String,
    ) : TdlibRequest {
        override fun toString() = "CheckPassword"
    }

    data object GetMe : TdlibRequest

    /** The calling code (e.g. `380`) of the country a phone number belongs to. */
    class GetCallingCode(
        val phoneNumber: String,
    ) : TdlibRequest {
        override fun toString() = "GetCallingCode"
    }

    /** Asks TDLib to load the next [limit] chats of [list]; answers [TdlibResponse.Failure] 404 when all are loaded. */
    data class LoadChats(
        val list: TdlibChatList,
        val limit: Int,
    ) : TdlibRequest

    data object LogOut : TdlibRequest
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

    /** The signed-in user; personal data, so [toString] is redacted. */
    class Me(
        val userId: Long,
        val firstName: String,
        val lastName: String,
        val phoneNumber: String,
    ) : TdlibResponse {
        override fun toString() = "Me"
    }

    class CallingCode(
        val code: String,
    ) : TdlibResponse {
        override fun toString() = "CallingCode"
    }
}

enum class TdlibChatType { Private, Secret, BasicGroup, Supergroup, Channel }

data class TdlibChatPosition(
    val list: TdlibChatList,
    /** Sort key within the list; 0 means the chat is no longer in the list. */
    val order: Long,
)

/** A chat as TDLib reports it. [toString] is redacted: titles are personal data. */
class TdlibChat(
    val id: Long,
    val type: TdlibChatType,
    val title: String,
    val unreadCount: Int,
    val positions: List<TdlibChatPosition>,
) {
    override fun toString() = "TdlibChat(id=$id)"
}

sealed interface TdlibUpdate {
    /**
     * TDLib's authorization state, by its TL name, e.g. `authorizationStateWaitPhoneNumber`.
     * [codeLength] is set for `authorizationStateWaitCode`, [passwordHint] for `authorizationStateWaitPassword`.
     */
    data class AuthorizationState(
        val state: String,
        val codeLength: Int? = null,
        val passwordHint: String? = null,
    ) : TdlibUpdate

    /** TDLib's connection state by its TL name, e.g. `connectionStateReady`, `connectionStateConnecting`. */
    data class ConnectionState(
        val state: String,
    ) : TdlibUpdate

    data class NewChat(
        val chat: TdlibChat,
    ) : TdlibUpdate {
        override fun toString() = "NewChat(${chat.id})"
    }

    class ChatTitle(
        val chatId: Long,
        val title: String,
    ) : TdlibUpdate {
        override fun toString() = "ChatTitle($chatId)"
    }

    data class ChatPosition(
        val chatId: Long,
        val position: TdlibChatPosition,
    ) : TdlibUpdate

    /** The complete position list after a last-message change. */
    data class ChatPositions(
        val chatId: Long,
        val positions: List<TdlibChatPosition>,
    ) : TdlibUpdate

    data class ChatUnread(
        val chatId: Long,
        val unreadCount: Int,
    ) : TdlibUpdate

    /** How many chats Telegram says [list] holds, loaded or not. */
    data class ChatCount(
        val list: TdlibChatList,
        val totalCount: Int,
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
