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
import java.time.ZoneId

data class StartedSession(
    val sessionId: SignInSessionId,
    val key: String,
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

    private fun zoneOrUtc(timeZone: String?): String =
        if (timeZone != null && timeZone in ZoneId.getAvailableZoneIds()) timeZone else "UTC"

    private companion object {
        val IDLE_LIMIT: Duration = Duration.ofDays(30)
        val MAX_AGE: Duration = Duration.ofDays(90)
        val BUMP_GAP: Duration = Duration.ofMinutes(1)
    }
}
