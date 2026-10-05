package telex.messaging.internal.account

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import telex.identity.OwnerId
import telex.messaging.LinkedAccountId
import telex.messaging.LinkedAccountState
import telex.messaging.MaskedPhone
import telex.telegram.TelegramSessionId
import java.time.Instant
import java.util.UUID

/** AC-04, AC-108, AC-115: the decision after Telegram reports who signed in. */
class LinkRulesTest {
    private val me = OwnerId(UUID.randomUUID())
    private val other = OwnerId(UUID.randomUUID())

    private fun account(
        owner: OwnerId,
        state: LinkedAccountState = LinkedAccountState.CONNECTED,
        telegramUserId: Long = 42,
    ) = LinkedAccount(
        id = LinkedAccountId(UUID.randomUUID()),
        ownerId = owner,
        telegramUserId = telegramUserId,
        telegramSessionId =
            if (state ==
                LinkedAccountState.SESSION_LOST
            ) {
                null
            } else {
                TelegramSessionId(UUID.randomUUID())
            },
        displayName = "Anna",
        phone = MaskedPhone("380", "42"),
        state = state,
        chatsTotal = null,
        chatSyncCompletedAt = null,
        createdAt = Instant.parse("2026-10-03T10:00:00Z"),
    )

    private fun refused(reason: LinkRefusal) = LinkDecision.Refused(reason)

    @Test
    fun `a Telegram user nobody holds under the limit is a new link`() {
        assertThat(LinkRules.decide(me, null, null, count = 2, limit = 3)).isEqualTo(LinkDecision.NewLink)
    }

    @Test
    fun `a Telegram user held by another Owner is refused (AC-04)`() {
        val decision = LinkRules.decide(me, null, account(other), count = 0, limit = 3)
        assertThat(decision).isEqualTo(refused(LinkRefusal.OTHER_OWNER))
    }

    @Test
    fun `another Owner wins over the limit and over a lost session (AC-04)`() {
        val lost = account(other, LinkedAccountState.SESSION_LOST)
        assertThat(LinkRules.decide(me, null, lost, count = 3, limit = 3)).isEqualTo(refused(LinkRefusal.OTHER_OWNER))
    }

    @Test
    fun `the same Telegram user already connected is already linked (AC-108)`() {
        val decision = LinkRules.decide(me, null, account(me), count = 3, limit = 3)
        assertThat(decision).isEqualTo(refused(LinkRefusal.ALREADY_LINKED))
    }

    @Test
    fun `a reconnecting account counts as connected for the duplicate rule (AC-108)`() {
        val decision = LinkRules.decide(me, null, account(me, LinkedAccountState.RECONNECTING), count = 1, limit = 3)
        assertThat(decision).isEqualTo(refused(LinkRefusal.ALREADY_LINKED))
    }

    @Test
    fun `the same Telegram user with a lost session signs in again with no limit check (AC-108, AC-115)`() {
        val lost = account(me, LinkedAccountState.SESSION_LOST)
        assertThat(LinkRules.decide(me, null, lost, count = 3, limit = 3)).isEqualTo(LinkDecision.SignedInAgain(lost))
    }

    @Test
    fun `an explicit Sign in again to the matching lost account brings back the same account`() {
        val lost = account(me, LinkedAccountState.SESSION_LOST)
        assertThat(LinkRules.decide(me, lost, lost, count = 3, limit = 3)).isEqualTo(LinkDecision.SignedInAgain(lost))
    }

    @Test
    fun `Sign in again as a different Telegram user nobody holds is a mismatch`() {
        val lost = account(me, LinkedAccountState.SESSION_LOST)
        assertThat(LinkRules.decide(me, lost, null, count = 1, limit = 3)).isEqualTo(refused(LinkRefusal.MISMATCH))
    }

    @Test
    fun `Sign in again as another of my own accounts is a mismatch`() {
        val lost = account(me, LinkedAccountState.SESSION_LOST)
        val mine = account(me, telegramUserId = 7)
        assertThat(LinkRules.decide(me, lost, mine, count = 2, limit = 3)).isEqualTo(refused(LinkRefusal.MISMATCH))
    }

    @Test
    fun `Sign in again as another Owner's Telegram user is other owner, not mismatch (AC-117)`() {
        val lost = account(me, LinkedAccountState.SESSION_LOST)
        val decision = LinkRules.decide(me, lost, account(other), count = 1, limit = 3)
        assertThat(decision).isEqualTo(refused(LinkRefusal.OTHER_OWNER))
    }

    @Test
    fun `a new account at the limit is refused, lost-session accounts included in the count (AC-115)`() {
        assertThat(LinkRules.decide(me, null, null, count = 3, limit = 3)).isEqualTo(refused(LinkRefusal.LIMIT))
    }

    @Test
    fun `a lowered limit below the count refuses new links and leaves existing ones alone (AC-115)`() {
        assertThat(LinkRules.decide(me, null, null, count = 3, limit = 1)).isEqualTo(refused(LinkRefusal.LIMIT))
        val lost = account(me, LinkedAccountState.SESSION_LOST)
        assertThat(LinkRules.decide(me, null, lost, count = 3, limit = 1)).isEqualTo(LinkDecision.SignedInAgain(lost))
    }

    @Test
    fun `the masked phone shows the country code and the last two digits only`() {
        assertThat(MaskedPhone("380", "42").toString()).isEqualTo("+380 ••• ••42")
    }
}
