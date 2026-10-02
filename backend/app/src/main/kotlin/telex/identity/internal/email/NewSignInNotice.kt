package telex.identity.internal.email

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import telex.identity.PublicUrl
import telex.identity.SignInSessionStarted
import telex.mail.Mailer
import telex.mail.OutgoingEmail
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** AC-98: tells the Owner about a later sign-in after commit; a failed send leaves the publication incomplete. */
@Component
class NewSignInNotice(
    private val jdbc: JdbcClient,
    private val mailer: Mailer,
    private val publicUrl: PublicUrl,
) {
    @ApplicationModuleListener
    fun on(event: SignInSessionStarted) {
        if (event.createdAccount) return
        val row =
            jdbc
                .sql(
                    "SELECT o.email, s.user_agent_label, s.device_type, s.time_zone, s.started_at " +
                        "FROM sign_in_session s JOIN owner o ON o.id = s.owner_id WHERE s.id = ?",
                ).param(event.sessionId.value)
                .query { rs, _ ->
                    Notice(
                        rs.getString("email"),
                        rs.getString("user_agent_label"),
                        rs.getString("device_type"),
                        rs.getString("time_zone"),
                        rs.getObject("started_at", OffsetDateTime::class.java),
                    )
                }.optional()
                .orElse(null) ?: return
        mailer.send(build(row))
    }

    private fun build(n: Notice): OutgoingEmail {
        val zone = runCatching { ZoneId.of(n.timeZone) }.getOrDefault(ZoneOffset.UTC)
        val local = n.startedAt.atZoneSameInstant(zone).format(LOCAL)
        val utc = n.startedAt.withOffsetSameInstant(ZoneOffset.UTC).format(UTC)
        return OutgoingEmail(
            to = n.email,
            subject = "New sign-in to teleX",
            text =
                "Someone signed in to your teleX account.\n\n" +
                    "Browser: ${n.label}\n" +
                    "Device type: ${n.deviceType}\n" +
                    "Time: $local (${zone.id})\n" +
                    "UTC: $utc\n\n" +
                    "If this was you, no action is needed. Otherwise review and end your sessions here:\n" +
                    "${publicUrl.link("/profile", fragment = "sessions")}\n",
            template = "new-sign-in",
        )
    }

    private data class Notice(
        val email: String,
        val label: String,
        val deviceType: String,
        val timeZone: String,
        val startedAt: OffsetDateTime,
    )

    private companion object {
        val LOCAL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
        val UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)
    }
}
