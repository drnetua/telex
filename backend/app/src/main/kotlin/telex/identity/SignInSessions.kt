package telex.identity

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import telex.identity.internal.device.DeviceLabel
import telex.identity.internal.secret.Secrets
import telex.identity.internal.session.SessionRows
import telex.shared.Uuid7
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class StartedSession(
    val sessionId: SignInSessionId,
    val key: String,
)

/** The authenticated principal: the Owner and the Sign-in Session they are using. */
data class SignedInOwner(
    val ownerId: OwnerId,
    val sessionId: SignInSessionId,
) : java.security.Principal {
    /** The OwnerId, so the framework's WebAuthn ceremonies name the user entity after the Owner. */
    override fun getName(): String = ownerId.value.toString()
}

data class MySession(
    val id: SignInSessionId,
    val userAgentLabel: String,
    val deviceType: String,
    val startedAt: Instant,
    val lastActivityAt: Instant,
    val current: Boolean,
)

sealed interface SessionResolution {
    data class Live(
        val ownerId: OwnerId,
        val sessionId: SignInSessionId,
    ) : SessionResolution

    data object Ended : SessionResolution

    data object Unknown : SessionResolution
}

/** The one session mechanism every sign-in method ends in (ADR-0001, ADR-0005). */
@Service
class SignInSessions(
    private val rows: SessionRows,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {
    @Transactional
    fun start(
        ownerId: OwnerId,
        heldKey: String?,
        userAgent: String?,
        timeZone: String?,
        createdAccount: Boolean,
    ): StartedSession {
        val now = clock.instant()
        heldKey?.let { rows.endByKeyHash(Secrets.sha256(it), now) }
        val id = SignInSessionId(Uuid7.next())
        val key = Secrets.newToken()
        rows.insert(id, ownerId, Secrets.sha256(key), DeviceLabel.from(userAgent), zoneOrUtc(timeZone), now)
        events.publishEvent(SignInSessionStarted(ownerId, id, createdAccount))
        return StartedSession(id, key)
    }

    @Transactional
    fun resolve(
        key: String,
        background: Boolean,
    ): SessionResolution {
        val row = rows.findByKeyHash(Secrets.sha256(key)) ?: return SessionResolution.Unknown
        val now = clock.instant()
        return when {
            row.endedAt != null -> {
                SessionResolution.Ended
            }

            row.lastActivityAt <= now.minus(IDLE_LIMIT) || row.startedAt <= now.minus(MAX_AGE) -> {
                rows.markEnded(row.id, now)
                SessionResolution.Ended
            }

            else -> {
                if (!background) rows.bumpActivity(row.id, now, BUMP_GAP)
                SessionResolution.Live(row.ownerId, row.id)
            }
        }
    }

    @Transactional
    fun endByKey(key: String?) {
        if (key != null) rows.endByKeyHash(Secrets.sha256(key), clock.instant())
    }

    @Transactional(readOnly = true)
    fun listMine(
        ownerId: OwnerId,
        currentId: SignInSessionId,
    ): List<MySession> {
        val now = clock.instant()
        return rows.listLive(ownerId, now.minus(IDLE_LIMIT), now.minus(MAX_AGE)).map {
            MySession(it.id, it.userAgentLabel, it.deviceType, it.startedAt, it.lastActivityAt, it.id == currentId)
        }
    }

    /** False when the session is not this Owner's live session (indistinguishable from missing). */
    @Transactional
    fun endMine(
        ownerId: OwnerId,
        sessionId: SignInSessionId,
    ): Boolean = rows.endOwned(ownerId, sessionId, clock.instant())

    @Transactional
    fun endMyOthers(
        ownerId: OwnerId,
        currentId: SignInSessionId,
    ) = rows.endOthers(ownerId, currentId, clock.instant())

    private fun zoneOrUtc(timeZone: String?): String =
        if (timeZone != null && timeZone in ZoneId.getAvailableZoneIds()) timeZone else "UTC"

    private companion object {
        val IDLE_LIMIT: Duration = Duration.ofDays(30)
        val MAX_AGE: Duration = Duration.ofDays(90)
        val BUMP_GAP: Duration = Duration.ofMinutes(1)
    }
}
