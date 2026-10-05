package telex

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import telex.identity.FixedClockConfiguration
import telex.identity.OwnerId
import telex.identity.SignInSessions
import telex.messaging.LinkedAccounts
import telex.messaging.Linking
import telex.messaging.LinkingOrigin
import telex.messaging.LinkingProgress
import telex.shared.DomainProblem
import telex.telegram.ChatSnapshot
import telex.telegram.ChatType
import telex.telegram.TelegramChatsChanged
import telex.telegram.TelegramSessionId
import telex.telegram.internal.fake.FakeTelegram
import java.nio.file.Files
import java.time.Duration
import java.util.Base64
import java.util.UUID

private const val PHONE = "9996610030"
private const val WRONG_CODE = "11111"
private const val WRONG_PASSWORD = "hunter2wrong"
private const val CHATS = 30

/** AC-03, AC-120 (QG-1b): no Telegram phone, name, id, chat title, code or password reaches logs, metrics or events. */
@SpringBootTest(
    properties = [
        "telex.telegram.adapter=fake",
        "logging.level.telex=DEBUG",
        "logging.level.org.springframework.modulith=DEBUG",
    ],
)
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
class TelegramDataHygieneIT {
    @Autowired lateinit var linking: Linking

    @Autowired lateinit var accounts: LinkedAccounts

    @Autowired lateinit var signInSessions: SignInSessions

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var meters: MeterRegistry

    @Autowired lateinit var events: ApplicationEventPublisher

    private fun refused(block: () -> Any?) {
        try {
            block()
        } catch (_: DomainProblem) {
            return
        }
        error("expected a refusal")
    }

    private fun serializedEvents() =
        jdbc.queryForList("SELECT serialized_event FROM event_publication", String::class.java).joinToString("\n")

    private fun meterText() =
        meters.meters.joinToString("\n") { m ->
            m.id.name + " " + m.id.tags.joinToString(" ") { "${it.key}=${it.value}" }
        }

    private fun count(sql: String) = jdbc.queryForObject(sql, Int::class.java)

    @Test
    fun `link, sync, wizard errors and unlink leave no Telegram data in the event registry, logs or metric tags`(
        output: CapturedOutput,
    ) {
        jdbc.execute("DELETE FROM event_publication")
        jdbc.execute("DELETE FROM channel")
        jdbc.execute("DELETE FROM linked_account")
        val uuid = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", uuid, "$uuid@mail.com", "$uuid@mail.com")
        val owner = OwnerId(uuid)
        val signIn = signInSessions.start(owner, null, null, null, false).sessionId

        linking.start(owner, signIn, LinkingOrigin.INBOX, null)
        refused { linking.submitPhone(owner, signIn, "000") }
        linking.submitPhone(owner, signIn, PHONE)
        refused { linking.submitCode(owner, signIn, WRONG_CODE) }
        linking.submitCode(owner, signIn, FakeTelegram.CODE)
        refused { linking.submitPassword(owner, signIn, WRONG_PASSWORD) }
        val done = linking.submitPassword(owner, signIn, FakeTelegram.PASSWORD) as LinkingProgress.Completed
        val session = jdbc.queryForObject("SELECT telegram_session_id FROM linked_account", UUID::class.java)!!
        val chats =
            List(CHATS) {
                ChatSnapshot(
                    -1_000_000_000_000L - it,
                    ChatType.Supergroup,
                    "Test chat ${it + 1}",
                    emptyList(),
                    false,
                    0,
                    it.toLong(),
                )
            }
        events.publishEvent(TelegramChatsChanged(TelegramSessionId(session), chats, emptyList(), CHATS, true))
        await().atMost(Duration.ofSeconds(10)).until { count("SELECT count(*) FROM channel") == CHATS }
        accounts.unlink(owner, done.linkedAccountId)
        await().atMost(Duration.ofSeconds(10)).until {
            count(
                "SELECT count(*) FROM event_publication " +
                    "WHERE event_type LIKE '%AccountUnlinked' AND completion_date IS NOT NULL",
            ) == 1
        }

        val events = serializedEvents()
        assertThat(events).contains(done.linkedAccountId.value.toString())
        val stores = mapOf("event_publication" to events, "logs" to output.all, "metrics" to meterText())
        val needles = listOf(PHONE, "Test user", "Test chat", "-1000000000000", FakeTelegram.PASSWORD, WRONG_PASSWORD)
        for ((store, text) in stores) {
            for (needle in needles) {
                assertThat(text).describedAs("%s must not hold '%s'", store, needle).doesNotContain(needle)
            }
            for (code in listOf(FakeTelegram.CODE, WRONG_CODE)) {
                assertThat(
                    text,
                ).describedAs("%s must not hold code %s", store, code).doesNotContainPattern("\\b$code\\b")
            }
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("telex.telegram.sessions-dir") { Files.createTempDirectory("telex-hygiene").toString() }
            registry.add("telex.telegram.api-id") { "54321" }
            registry.add("telex.telegram.api-hash") { "abcdef" }
            registry.add("telex.master-key") { Base64.getEncoder().encodeToString(ByteArray(32) { 9 }) }
        }
    }
}
