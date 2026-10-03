package telex.agents.call

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import telex.TestcontainersConfiguration
import telex.agents.ModelProfileId
import telex.agents.ModelSlotKind
import telex.agents.ProfileCallResult
import telex.agents.ProfileCalls
import telex.agents.ProfileRef
import telex.agents.internal.profile.ModelProfile
import telex.agents.internal.profile.ProfileRepository
import telex.identity.OwnerId
import telex.llm.Attempt
import telex.llm.AttemptOutcome
import telex.llm.CatalogModel
import telex.llm.CatalogSnapshot
import telex.llm.CatalogState
import telex.llm.ChatMessage
import telex.llm.Modality
import telex.llm.ModelCatalog
import telex.llm.ModelId
import telex.llm.SlotRequest
import telex.shared.Uuid7
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** A stalled first model through ProfileCalls (AC-224, sad QG-1): timeout, then the backup answers. */
@SpringBootTest(
    properties = [
        "telex.llm.openrouter.api-key=sk-or-it-key",
        "telex.llm.attempt-timeout=500ms",
    ],
)
@Import(TestcontainersConfiguration::class, ProfileCallsStalledIT.FixedCatalog::class)
class ProfileCallsStalledIT {
    @TestConfiguration
    class FixedCatalog {
        @Bean
        @Primary
        fun stalledCatalog(): ModelCatalog =
            object : ModelCatalog {
                private val text = setOf(Modality.TEXT)
                private val snap =
                    CatalogSnapshot(
                        listOf("it/a", "it/b")
                            .map {
                                CatalogModel(
                                    ModelId(it),
                                    it,
                                    "prov",
                                    text,
                                    text,
                                    BigDecimal.ONE,
                                    BigDecimal.ONE,
                                    null,
                                    CONTEXT_LENGTH,
                                )
                            }.associateBy { it.modelId },
                        Instant.now(),
                        null,
                        CatalogState.CURRENT,
                    )

                override fun snapshot() = snap

                override fun find(modelId: ModelId) = snap.models[modelId]
            }
    }

    companion object {
        private const val CONTEXT_LENGTH = 128_000
        private const val STALL_MILLIS = 5_000
        private const val REQUEST_WAIT_MILLIS = 5_000L
        private const val POLL_MILLIS = 5L
        private const val JOIN_MILLIS = 10_000L
        private val wm = WireMockServer(options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun up() = wm.start()

        @AfterAll
        @JvmStatic
        fun down() = wm.stop()

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            wm.start()
            registry.add("telex.llm.openrouter.base-url") { "http://localhost:${wm.port()}" }
        }
    }

    @Autowired lateinit var calls: ProfileCalls

    @Autowired lateinit var profiles: ProfileRepository

    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `stalled main model times out and the backup answers with fallback (AC-224)`() {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        val owner = OwnerId(id)
        val a = ModelId("it/a")
        val b = ModelId("it/b")
        val chains =
            mapOf(
                ModelSlotKind.TEXT to listOf(a, b),
                ModelSlotKind.VISION to emptyList(),
                ModelSlotKind.IMAGE to emptyList(),
            )
        val profile = ModelProfile(ModelProfileId(Uuid7.next()), "stalled", chains)
        profiles.insert(owner, profile, Instant.now())
        wm.stubFor(
            post(urlEqualTo("/chat/completions"))
                .withRequestBody(containing("\"it/a\""))
                .willReturn(aResponse().withStatus(200).withFixedDelay(STALL_MILLIS).withBody("{}")),
        )
        wm.stubFor(
            post(urlEqualTo("/chat/completions"))
                .withRequestBody(containing("\"it/b\""))
                .willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                        """{"id":"x","choices":[{"index":0,"finish_reason":"stop",""" +
                            """"message":{"role":"assistant","content":"hi"}}]}""",
                    ),
                ),
        )
        val r =
            calls.call(
                owner,
                ProfileRef.Custom(profile.id),
                ModelSlotKind.TEXT,
                SlotRequest.Text(listOf(ChatMessage("user", "hello"))),
            ) as ProfileCallResult.Answered
        assertThat(r.answeredBy).isEqualTo(b)
        assertThat(r.attempts).containsExactly(Attempt(a, AttemptOutcome.TIMEOUT), Attempt(b, AttemptOutcome.ANSWERED))
        val row = jdbc.queryForMap("SELECT * FROM model_call WHERE id = ?", r.callId.value)
        assertThat(row["answered_by_model_id"]).isEqualTo("it/b")
        assertThat(row["fallback"]).isEqualTo(true)
        assertThat(
            jdbc.queryForList(
                "SELECT model_id, outcome FROM model_call_attempt WHERE model_call_id = ? ORDER BY position",
                r.callId.value,
            ),
        ).containsExactly(
            mapOf("model_id" to "it/a", "outcome" to "timeout"),
            mapOf("model_id" to "it/b", "outcome" to "answered"),
        )
    }

    @Test
    fun `a call interrupted on a virtual thread still records its timeout attempt and keeps the flag (AC-229)`() {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        val owner = OwnerId(id)
        val chains =
            mapOf(
                ModelSlotKind.TEXT to listOf(ModelId("it/a")),
                ModelSlotKind.VISION to emptyList(),
                ModelSlotKind.IMAGE to emptyList(),
            )
        val profile = ModelProfile(ModelProfileId(Uuid7.next()), "interrupted", chains)
        profiles.insert(owner, profile, Instant.now())
        wm.stubFor(
            post(urlEqualTo("/chat/completions"))
                .withRequestBody(containing("\"it/a\""))
                .willReturn(aResponse().withStatus(200).withFixedDelay(STALL_MILLIS).withBody("{}")),
        )
        wm.resetRequests()
        val result = AtomicReference<ProfileCallResult>()
        val flagOnReturn = AtomicReference<Boolean>()
        val thread =
            Thread.ofVirtual().start {
                result.set(
                    calls.call(
                        owner,
                        ProfileRef.Custom(profile.id),
                        ModelSlotKind.TEXT,
                        SlotRequest.Text(listOf(ChatMessage("user", "hello"))),
                    ),
                )
                flagOnReturn.set(Thread.currentThread().isInterrupted)
            }
        // Interrupt only once the attempt on it/a is in flight, not during the profile lookup before it.
        val deadline = System.currentTimeMillis() + REQUEST_WAIT_MILLIS
        while (wm.findAll(postRequestedFor(urlEqualTo("/chat/completions"))).isEmpty()) {
            check(System.currentTimeMillis() < deadline) { "the call never reached the provider" }
            Thread.sleep(POLL_MILLIS)
        }
        thread.interrupt()
        thread.join(JOIN_MILLIS)
        assertThat(thread.isAlive).describedAs("the interrupted call returned").isFalse()
        val r = result.get() as ProfileCallResult.Failed
        assertThat(flagOnReturn.get()).isTrue()
        assertThat(
            jdbc.queryForList(
                "SELECT model_id, outcome FROM model_call_attempt WHERE model_call_id = ? ORDER BY position",
                r.callId.value,
            ),
        ).containsExactly(mapOf("model_id" to "it/a", "outcome" to "timeout"))
    }
}
