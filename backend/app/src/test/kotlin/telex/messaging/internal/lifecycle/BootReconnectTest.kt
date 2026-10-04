package telex.messaging.internal.lifecycle

import org.junit.jupiter.api.Test
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import telex.identity.OwnerId
import telex.identity.OwnerKeys
import telex.messaging.LinkedAccountId
import telex.messaging.LinkedAccountState
import telex.messaging.MaskedPhone
import telex.messaging.internal.account.LinkedAccount
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.keyAad
import telex.shared.Uuid7
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessions
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * The boot side of an unlink that raced a reopen (AC-111). The fake can't reach this path any more: once the unlink
 * destroyed the directory, the fake's reopen comes up signed out. Real TDLib can still register a signed-in session
 * after the unlink gave up waiting, and only this sign-out removes the teleX device from Telegram.
 */
class BootReconnectTest {
    private val rows = mock(LinkedAccountRows::class.java)
    private val ownerKeys = mock(OwnerKeys::class.java)
    private val telegram = mock(TelegramSessions::class.java)
    private val boot =
        BootReconnect(
            rows,
            ownerKeys,
            telegram,
            mock(LinkedAccountStates::class.java),
            mock(SessionStateListener::class.java),
            mock(LifecycleMetrics::class.java),
        )

    private val session = TelegramSessionId(Uuid7.next())
    private val account =
        LinkedAccount(
            id = LinkedAccountId(Uuid7.next()),
            ownerId = OwnerId(Uuid7.next()),
            telegramUserId = 42,
            telegramSessionId = session,
            displayName = "Ann",
            phone = MaskedPhone("44", "12"),
            state = LinkedAccountState.CONNECTED,
            chatsTotal = null,
            chatSyncCompletedAt = null,
            createdAt = Instant.EPOCH,
        )

    /** Boot finds [account] to reopen; after the reopen the row is held by it only when [stillLinked]. */
    private fun bootWith(stillLinked: Boolean) {
        val sealed = byteArrayOf(1, 2, 3)
        `when`(rows.allSessionIds()).thenReturn(setOf(session))
        `when`(rows.allNotSessionLost()).thenReturn(listOf(account))
        `when`(rows.sealedKey(account.id)).thenReturn(sealed)
        `when`(ownerKeys.open(account.ownerId, sealed, account.id.keyAad())).thenReturn(ByteArray(32))
        `when`(rows.findBySession(session)).thenReturn(if (stillLinked) account else null)
    }

    @Test
    fun `a session reopened for an account unlinked meanwhile is signed out, then closed and destroyed (AC-111)`() {
        bootWith(stillLinked = false)

        boot.run().get(5, TimeUnit.SECONDS)

        val order = inOrder(telegram)
        order.verify(telegram).reopen(session, ByteArray(32))
        order.verify(telegram).logOut(session, Duration.ofSeconds(10))
        order.verify(telegram).close(session)
        order.verify(telegram).destroy(session)
    }

    @Test
    fun `a sign-out that throws still closes and destroys the session of an unlinked account (AC-111)`() {
        bootWith(stillLinked = false)
        doThrow(
            IllegalStateException("Telegram is unavailable"),
        ).`when`(telegram).logOut(session, Duration.ofSeconds(10))

        boot.run().get(5, TimeUnit.SECONDS)

        verify(telegram).close(session)
        // the failed reopen's cleanup destroys it again; destroying an absent directory is a no-op
        verify(telegram, atLeastOnce()).destroy(session)
    }

    @Test
    fun `a session whose account is still linked is neither signed out nor closed (AC-111)`() {
        bootWith(stillLinked = true)

        boot.run().get(5, TimeUnit.SECONDS)

        verify(telegram).reopen(session, ByteArray(32))
        verify(telegram, never()).logOut(session, Duration.ofSeconds(10))
        verify(telegram, never()).close(session)
        verify(telegram, never()).destroy(session)
    }
}
