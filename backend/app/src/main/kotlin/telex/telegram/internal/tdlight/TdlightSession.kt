package telex.telegram.internal.tdlight

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import telex.telegram.ChatSnapshot
import telex.telegram.ChatType
import telex.telegram.SessionState
import telex.telegram.TelegramChatsChanged
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessionStateChanged
import telex.telegram.tdlib.TdlibChat
import telex.telegram.tdlib.TdlibChatList
import telex.telegram.tdlib.TdlibChatPosition
import telex.telegram.tdlib.TdlibChatType
import telex.telegram.tdlib.TdlibClient
import telex.telegram.tdlib.TdlibRequest
import telex.telegram.tdlib.TdlibResponse
import telex.telegram.tdlib.TdlibUpdate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/** How a sign-in step ended: the next authorization state, or an error answer. */
internal sealed interface StepResult {
    data class State(
        val update: TdlibUpdate.AuthorizationState,
    ) : StepResult

    data class Error(
        val failure: TdlibResponse.Failure,
    ) : StepResult
}

/**
 * One Linked Account's TDLib client and what teleX derived from its updates. Callbacks arrive on TDLib's threads;
 * state is changed under the session lock, so each event gets its sequence in TDLib's order, and events are published
 * on one virtual thread per session, in that order (sad §8, Concurrency).
 */
@Suppress("TooManyFunctions") // one small handler per kind of TDLib update
internal class TdlightSession(
    val id: TelegramSessionId,
    private val reopened: Boolean,
    private val events: ApplicationEventPublisher,
) {
    private class ChatEntry(
        var type: TdlibChatType,
        var title: String,
        var unread: Int,
        val positions: MutableMap<TdlibChatList, Long>,
    ) {
        val visible get() = TdlibChatList.Main in positions || TdlibChatList.Archive in positions
    }

    private val clientFuture = CompletableFuture<TdlibClient>()
    private val publisher = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("telegram-events-", 0).factory())

    /** Completes when TDLib first asks for a phone number (a fresh session is ready for the sign-in). */
    val waitingForPhone = CompletableFuture<Unit>()

    /** Completes when TDLib reports `authorizationStateClosed`. */
    val closed = CompletableFuture<Unit>()

    @Volatile var waiter: CompletableFuture<StepResult>? = null

    @Volatile var passwordHint: String? = null
        private set

    private var sequence = 0L
    private var authorized = false
    private var lost = false
    private var closing = false
    private var lastState: SessionState? = null
    private var syncStarted = reopened
    private var loadStarted = false
    private val counts = HashMap<TdlibChatList, Int>()
    private var loadCompleted = false
    private val chats = LinkedHashMap<Long, ChatEntry>()
    private val dirtyUpserts = LinkedHashSet<Long>()
    private val dirtyRemoved = LinkedHashSet<Long>()

    fun attach(client: TdlibClient) {
        clientFuture.complete(client)
    }

    fun client(): TdlibClient = clientFuture.get()

    @Synchronized fun markClosing() {
        closing = true
    }

    /**
     * A fresh session holds back its state and chat events until the Linked Account exists, because the listeners
     * drop events of a session no account holds; this announces the latest state and starts the chat load.
     */
    @Synchronized
    fun startSync() {
        if (syncStarted) return
        syncStarted = true
        lastState?.let { state -> publish(stateEvent(state)) }
        if (authorized && !lost) startChatLoad()
    }

    fun dispose() {
        publisher.shutdown()
    }

    @Synchronized
    fun onUpdate(update: TdlibUpdate) {
        when (update) {
            is TdlibUpdate.AuthorizationState -> {
                onAuthorization(update)
            }

            is TdlibUpdate.ConnectionState -> {
                onConnection(update)
            }

            is TdlibUpdate.NewChat -> {
                onNewChat(update.chat)
            }

            is TdlibUpdate.ChatTitle -> {
                change(update.chatId) { it.title = update.title }
            }

            is TdlibUpdate.ChatUnread -> {
                change(update.chatId) { it.unread = update.unreadCount }
            }

            is TdlibUpdate.ChatPosition -> {
                change(update.chatId) { it.apply(update.position) }
            }

            is TdlibUpdate.ChatPositions -> {
                change(update.chatId) {
                    it.positions.clear()
                    update.positions.forEach { position -> it.apply(position) }
                }
            }

            TdlibUpdate.Closed -> {
                onClosed()
            }

            is TdlibUpdate.Failed -> {
                onFailed(update)
            }

            is TdlibUpdate.ChatCount -> {
                counts[update.list] = update.totalCount
            }

            is TdlibUpdate.Other -> {
                Unit
            }
        }
    }

    private fun onAuthorization(update: TdlibUpdate.AuthorizationState) {
        when (update.state) {
            STATE_WAIT_PHONE -> {
                onWaitPhone()
            }

            STATE_WAIT_PASSWORD -> {
                passwordHint = update.passwordHint
            }

            STATE_READY -> {
                authorized = true
                emitState(SessionState.Ready)
                if (syncStarted) startChatLoad()
            }

            STATE_LOGGING_OUT -> {
                if (authorized) emitState(SessionState.Closed)
            }
        }
        waiter?.complete(StepResult.State(update))
    }

    private fun onWaitPhone() {
        if (!reopened) {
            waitingForPhone.complete(Unit)
            return
        }
        // A stored session that asks for a phone number again was ended while teleX was stopped.
        if (!closing) {
            emitState(SessionState.Closed)
            closing = true
            publisher.execute { client().close() }
        }
    }

    private fun onConnection(update: TdlibUpdate.ConnectionState) {
        if (!authorized || lost) return
        emitState(if (update.state == CONNECTION_READY) SessionState.Ready else SessionState.Connecting)
    }

    private fun onClosed() {
        if (authorized && !closing) emitState(SessionState.Closed)
        waitingForPhone.completeExceptionally(IllegalStateException("closed"))
        waiter?.completeExceptionally(IllegalStateException("closed"))
        closed.complete(Unit)
    }

    private fun onFailed(update: TdlibUpdate.Failed) {
        log.warn("TDLib client {} reported {}", id.value, update.cause.javaClass.simpleName)
        waitingForPhone.completeExceptionally(update.cause)
        waiter?.completeExceptionally(update.cause)
    }

    private fun emitState(state: SessionState) {
        if (state == lastState || lost) return
        lastState = state
        if (state == SessionState.Closed) lost = true
        if (syncStarted) publish(stateEvent(state))
    }

    private fun stateEvent(state: SessionState): () -> Unit {
        val event = TelegramSessionStateChanged(id, state, ++sequence)
        return { events.publishEvent(event) }
    }

    private fun onNewChat(chat: TdlibChat) {
        val entry = ChatEntry(chat.type, chat.title, chat.unreadCount, HashMap())
        chat.positions.forEach { entry.apply(it) }
        val wasVisible = chats.put(chat.id, entry)?.visible ?: false
        afterChange(chat.id, entry, wasVisible)
    }

    private fun change(
        chatId: Long,
        mutation: (ChatEntry) -> Unit,
    ) {
        val entry = chats[chatId] ?: return
        val wasVisible = entry.visible
        mutation(entry)
        afterChange(chatId, entry, wasVisible)
    }

    private fun ChatEntry.apply(position: TdlibChatPosition) {
        if (position.order == 0L) positions.remove(position.list) else positions[position.list] = position.order
    }

    private fun afterChange(
        chatId: Long,
        entry: ChatEntry,
        wasVisible: Boolean,
    ) {
        if (entry.visible) {
            dirtyUpserts += chatId
            dirtyRemoved -= chatId
        } else if (wasVisible) {
            dirtyRemoved += chatId
            dirtyUpserts -= chatId
        }
        if (loadCompleted) flush()
    }

    /** Telegram's total for both lists once it said so, the loaded count once the load is done, else unknown. */
    private fun total(): Int? =
        when {
            loadCompleted -> chats.values.count { it.visible }
            TdlibChatList.Main in counts && TdlibChatList.Archive in counts -> counts.values.sum()
            else -> null
        }

    private fun flush(force: Boolean = false) {
        if (!force && dirtyUpserts.isEmpty() && dirtyRemoved.isEmpty()) return
        val event =
            TelegramChatsChanged(
                sessionId = id,
                upserted = dirtyUpserts.mapNotNull { chatId -> chats[chatId]?.let { snapshot(chatId, it) } },
                removedChatIds = dirtyRemoved.toList(),
                total = total(),
                loadCompleted = loadCompleted,
                loadedChatIds = if (force) chats.filterValues { it.visible }.keys.toSet() else null,
            )
        dirtyUpserts.clear()
        dirtyRemoved.clear()
        publish { events.publishEvent(event) }
    }

    private fun snapshot(
        chatId: Long,
        entry: ChatEntry,
    ): ChatSnapshot {
        val main = entry.positions[TdlibChatList.Main]
        return ChatSnapshot(
            chatId = chatId,
            type = chatType(entry.type),
            title = entry.title,
            folderIds =
                entry.positions.keys
                    .filterIsInstance<TdlibChatList.Folder>()
                    .map { it.id }
                    .sorted(),
            archived = main == null && TdlibChatList.Archive in entry.positions,
            unreadCount = entry.unread,
            order = main ?: entry.positions[TdlibChatList.Archive] ?: 0L,
        )
    }

    private fun chatType(type: TdlibChatType) =
        when (type) {
            TdlibChatType.Private -> ChatType.Private
            TdlibChatType.Secret -> ChatType.Secret
            TdlibChatType.BasicGroup -> ChatType.BasicGroup
            TdlibChatType.Supergroup -> ChatType.Supergroup
            TdlibChatType.Channel -> ChatType.Channel
        }

    private fun startChatLoad() {
        if (loadStarted) return
        loadStarted = true
        Thread.ofVirtual().name("telegram-chat-load-", 0).start { loadChats() }
    }

    /** Loads the main and archived lists; TDLib answers 404 when a list is fully loaded. */
    @Suppress("TooGenericExceptionCaught") // a failed load is logged; the list stays incomplete until the next start
    private fun loadChats() {
        try {
            val client = client()
            for (list in listOf(TdlibChatList.Main, TdlibChatList.Archive)) {
                do {
                    val response =
                        client
                            .send(
                                TdlibRequest.LoadChats(list, LOAD_BATCH),
                            ).get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    synchronized(this) { flush() }
                    val allLoaded = response is TdlibResponse.Failure && response.code == NOT_FOUND
                    check(allLoaded || response !is TdlibResponse.Failure) {
                        "loadChats failed with ${(response as TdlibResponse.Failure).code}"
                    }
                } while (!allLoaded)
            }
            synchronized(this) {
                loadCompleted = true
                flush(force = true)
            }
        } catch (e: Exception) {
            log.warn("Chat list load for {} stopped: {}", id.value, e.javaClass.simpleName)
        }
    }

    private fun publish(task: () -> Unit) {
        try {
            publisher.execute(task)
        } catch (_: RejectedExecutionException) {
            // The session was released; late TDLib updates are dropped.
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(TdlightSession::class.java)

        const val STATE_WAIT_PHONE = "authorizationStateWaitPhoneNumber"
        const val STATE_WAIT_PASSWORD = "authorizationStateWaitPassword"
        const val STATE_READY = "authorizationStateReady"
        const val STATE_LOGGING_OUT = "authorizationStateLoggingOut"
        const val CONNECTION_READY = "connectionStateReady"
        const val LOAD_BATCH = 100
        const val LOAD_TIMEOUT_SECONDS = 60L
        const val NOT_FOUND = 404
    }
}
