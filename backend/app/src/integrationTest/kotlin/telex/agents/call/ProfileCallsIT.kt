package telex.agents.call

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import telex.TestcontainersConfiguration
import telex.agents.ModelCallFinished
import telex.agents.ModelCallOutcome
import telex.agents.ModelProfileId
import telex.agents.ModelSlotKind
import telex.agents.ProfileCallResult
import telex.agents.ProfileCalls
import telex.agents.ProfileRef
import telex.agents.SlotFailure
import telex.agents.SystemProfileKey
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
import telex.llm.SlotAnswer
import telex.llm.SlotRequest
import telex.shared.Uuid7
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Profile slot calls and call records (T13): AC-10, AC-223, AC-224, AC-228, AC-229 against WireMock standing in for
 * OpenRouter, with Owner scoping (AC-222) and the no-content rule.
 */
@SpringBootTest(
    properties = [
        "telex.llm.openrouter.api-key=sk-or-it-key",
        "telex.models.system-profiles.balanced.text=it/gone,it/b",
        "telex.models.system-profiles.balanced.vision=it/vision",
        "telex.models.system-profiles.balanced.image=it/image",
    ],
)
@RecordApplicationEvents
@Import(TestcontainersConfiguration::class, ProfileCallsIT.FakeCatalog::class)
class ProfileCallsIT {
    class SwitchableCatalog : ModelCatalog {
        @Volatile var current: CatalogSnapshot = CatalogSnapshot(emptyMap(), null, null, CatalogState.NOT_LOADED)

        override fun snapshot() = current

        override fun find(modelId: ModelId) = current.models[modelId]
    }

    open class FinishedListener {
        @ApplicationModuleListener
        open fun on(event: ModelCallFinished) {
            received += event
        }
    }

    @TestConfiguration
    class FakeCatalog {
        @Bean
        fun finishedListener() = FinishedListener()

        @Bean
        @Primary
        fun callsSwitchableCatalog(): ModelCatalog = SwitchableCatalog()
    }

    companion object {
        val received = CopyOnWriteArrayList<ModelCallFinished>()
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

    @Autowired lateinit var catalog: ModelCatalog

    @Autowired lateinit var profiles: ProfileRepository

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var events: ApplicationEvents

    @Autowired lateinit var incomplete: IncompleteEventPublications

    @Autowired lateinit var meters: MeterRegistry

    private val promptText = "PROMPT-SECRET-4711"
    private val answerText = "ANSWER-SECRET-0815"
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 1, 2, 3)
    private val a = ModelId("it/a")
    private val b = ModelId("it/b")
    private val gone = ModelId("it/gone")
    private val text = setOf(Modality.TEXT)
    private val request = SlotRequest.Text(listOf(ChatMessage("user", promptText)))
    private val logs = ListAppender<ILoggingEvent>()
    private val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    private val balanced = ProfileRef.System(SystemProfileKey.BALANCED)

    private fun model(
        id: String,
        takes: Set<Modality> = text,
        produces: Set<Modality> = text,
    ) = CatalogModel(ModelId(id), id, "prov", takes, produces, BigDecimal.ONE, BigDecimal.ONE, null, 128_000)

    @BeforeEach
    fun setUp() {
        wm.resetAll()
        (catalog as SwitchableCatalog).current =
            CatalogSnapshot(
                listOf(
                    model("it/a"),
                    model("it/b"),
                    model("it/vision", setOf(Modality.TEXT, Modality.IMAGE)),
                    model("it/image", text, setOf(Modality.IMAGE)),
                ).associateBy { it.modelId },
                Instant.now(),
                null,
                CatalogState.CURRENT,
            )
        logs.start()
        root.addAppender(logs)
        root.level = Level.DEBUG
    }

    @AfterEach
    fun tearDown() {
        root.detachAppender(logs)
        jdbc.execute("DROP TRIGGER IF EXISTS it_fail_call ON model_call")
        jdbc.execute("DROP FUNCTION IF EXISTS it_fail_call()")
    }

    private fun owner(): OwnerId {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        return OwnerId(id)
    }

    private fun custom(
        owner: OwnerId,
        textChain: List<ModelId> = listOf(a, b),
        vision: List<ModelId> = emptyList(),
        image: List<ModelId> = emptyList(),
    ): ProfileRef.Custom {
        val profile =
            ModelProfile(
                ModelProfileId(Uuid7.next()),
                "p-${UUID.randomUUID()}".take(20),
                mapOf(ModelSlotKind.TEXT to textChain, ModelSlotKind.VISION to vision, ModelSlotKind.IMAGE to image),
            )
        profiles.insert(owner, profile, Instant.now())
        return ProfileRef.Custom(profile.id)
    }

    private fun ok(body: String = answerText) =
        aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
            """{"id":"x","choices":[{"index":0,"finish_reason":"stop",""" +
                """"message":{"role":"assistant","content":"$body"}}]}""",
        )

    private fun err(status: Int) =
        aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"error":{"code":$status,"message":"boom"}}""")

    private fun stub(
        model: ModelId,
        response: ResponseDefinitionBuilder,
    ) = wm.stubFor(
        post(urlEqualTo("/chat/completions")).withRequestBody(containing("\"${model.value}\"")).willReturn(response),
    )

    private fun hits(model: ModelId) =
        wm.findAll(postRequestedFor(urlEqualTo("/chat/completions")).withRequestBody(containing("\"${model.value}\"")))

    private fun call(
        owner: OwnerId,
        ref: ProfileRef,
        slot: ModelSlotKind = ModelSlotKind.TEXT,
        req: SlotRequest = request,
    ) = calls.call(owner, ref, slot, req)

    private fun rows(callId: UUID) = jdbc.queryForList("SELECT * FROM model_call WHERE id = ?", callId)

    private fun attemptRows(callId: UUID) =
        jdbc.queryForList(
            "SELECT model_id, outcome FROM model_call_attempt WHERE model_call_id = ? ORDER BY position",
            callId,
        )

    private fun finished(callId: UUID) =
        events
            .stream(ModelCallFinished::class.java)
            .filter {
                it.callId.value == callId
            }.toList()

    @Test
    fun `main model answers - no fallback, one record, one attempt, event published (AC-229)`() {
        val o = owner()
        val ref = custom(o)
        stub(a, ok())
        val before = Instant.now().minusSeconds(1)
        val r = call(o, ref)
        r as ProfileCallResult.Answered
        assertThat(r.answer).isEqualTo(SlotAnswer.Text(answerText))
        assertThat(r.answeredBy).isEqualTo(a)
        assertThat(r.fallback).isFalse()
        assertThat(r.attempts).containsExactly(Attempt(a, AttemptOutcome.ANSWERED))
        val row = rows(r.callId.value).single()
        assertThat(row["owner_id"]).isEqualTo(o.value)
        assertThat(row["custom_profile_id"]).isEqualTo((ref.id).value)
        assertThat(row["system_profile_key"]).isNull()
        assertThat(row["slot"]).isEqualTo("text")
        assertThat(row["outcome"]).isEqualTo("answered")
        assertThat(row["answered_by_model_id"]).isEqualTo("it/a")
        assertThat(row["fallback"]).isEqualTo(false)
        assertThat((row["started_at"] as java.sql.Timestamp).toInstant()).isAfter(before)
        assertThat(attemptRows(r.callId.value)).containsExactly(mapOf("model_id" to "it/a", "outcome" to "answered"))
        val event = finished(r.callId.value).single()
        assertThat(event.ownerId).isEqualTo(o)
        assertThat(event.profile).isEqualTo(ref)
        assertThat(event.slot).isEqualTo(ModelSlotKind.TEXT)
        assertThat(event.outcome).isEqualTo(ModelCallOutcome.ANSWERED)
        assertThat(event.answeredByModelId).isEqualTo(a)
        assertThat(event.fallback).isFalse()
    }

    @Test
    fun `system profile with a main model missing from the catalog - backup answers with fallback (AC-10, AC-229)`() {
        val o = owner()
        stub(b, ok())
        val r = call(o, balanced) as ProfileCallResult.Answered
        assertThat(r.answeredBy).isEqualTo(b)
        assertThat(r.fallback).isTrue()
        assertThat(r.attempts).containsExactly(
            Attempt(gone, AttemptOutcome.MISSING),
            Attempt(b, AttemptOutcome.ANSWERED),
        )
        assertThat(hits(gone)).isEmpty()
        val row = rows(r.callId.value).single()
        assertThat(row["system_profile_key"]).isEqualTo("balanced")
        assertThat(row["custom_profile_id"]).isNull()
        assertThat(row["fallback"]).isEqualTo(true)
        assertThat(attemptRows(r.callId.value)).containsExactly(
            mapOf("model_id" to "it/gone", "outcome" to "missing"),
            mapOf("model_id" to "it/b", "outcome" to "answered"),
        )
    }

    @Test
    fun `main model returns to the catalog - it is used again (AC-10)`() {
        val o = owner()
        val ref = custom(o, listOf(gone, b))
        stub(b, ok())
        assertThat((call(o, ref) as ProfileCallResult.Answered).answeredBy).isEqualTo(b)
        (catalog as SwitchableCatalog).let { c ->
            c.current = c.current.copy(models = c.current.models + (gone to model("it/gone")))
        }
        stub(gone, ok())
        val r = call(o, ref) as ProfileCallResult.Answered
        assertThat(r.answeredBy).isEqualTo(gone)
        assertThat(r.fallback).isFalse()
    }

    @Test
    fun `rate limited main model - same request answered by the backup, next request starts from A (AC-224)`() {
        val o = owner()
        val ref = custom(o)
        stub(a, err(429))
        stub(b, ok())
        val r = call(o, ref) as ProfileCallResult.Answered
        assertThat(r.answeredBy).isEqualTo(b)
        assertThat(r.fallback).isTrue()
        assertThat(r.attempts).containsExactly(
            Attempt(a, AttemptOutcome.RATE_LIMITED),
            Attempt(b, AttemptOutcome.ANSWERED),
        )
        assertThat(rows(r.callId.value).single()["fallback"]).isEqualTo(true)
        wm.resetRequests()
        call(o, ref)
        assertThat(hits(a)).hasSize(1)
    }

    @Test
    fun `content refusal stops at once without trying the backup (AC-228)`() {
        val o = owner()
        val ref = custom(o)
        stub(a, err(403))
        stub(b, ok())
        val r = call(o, ref) as ProfileCallResult.Failed
        assertThat(r.reason).isEqualTo(SlotFailure.NO_MODEL_ANSWERED)
        assertThat(r.attempts).containsExactly(Attempt(a, AttemptOutcome.CONTENT_REFUSED))
        assertThat(hits(b)).isEmpty()
        assertThat(rows(r.callId.value).single()["outcome"]).isEqualTo("no-model-answered")
        assertThat(rows(r.callId.value).single()["answered_by_model_id"]).isNull()
    }

    @Test
    fun `invalid request stops at once (AC-228)`() {
        val o = owner()
        val ref = custom(o)
        stub(a, err(422))
        stub(b, ok())
        val r = call(o, ref) as ProfileCallResult.Failed
        assertThat(r.attempts).containsExactly(Attempt(a, AttemptOutcome.INVALID_REQUEST))
        assertThat(hits(b)).isEmpty()
    }

    @Test
    fun `both models fail - no model answered with every attempt and reason (AC-228)`() {
        val o = owner()
        val ref = custom(o)
        stub(a, err(500))
        stub(b, err(429))
        val r = call(o, ref) as ProfileCallResult.Failed
        assertThat(r.reason).isEqualTo(SlotFailure.NO_MODEL_ANSWERED)
        assertThat(r.attempts).containsExactly(
            Attempt(a, AttemptOutcome.PROVIDER_ERROR),
            Attempt(b, AttemptOutcome.RATE_LIMITED),
        )
        assertThat(attemptRows(r.callId.value)).hasSize(2)
        assertThat(rows(r.callId.value).single()["fallback"]).isEqualTo(true)
        val event = finished(r.callId.value).single()
        assertThat(event.fallback).isTrue()
        assertThat(event.outcome).isEqualTo(ModelCallOutcome.NO_MODEL_ANSWERED)
        assertThat(event.answeredByModelId).isNull()
    }

    @Test
    fun `empty vision slot fails with no model available, no provider call, never the text model (AC-223)`() {
        val o = owner()
        val ref = custom(o)
        stub(a, ok())
        stub(b, ok())
        val req = SlotRequest.Vision(listOf(ChatMessage("user", promptText)), emptyList())
        val r = call(o, ref, ModelSlotKind.VISION, req) as ProfileCallResult.Failed
        assertThat(r.reason).isEqualTo(SlotFailure.NO_MODEL_AVAILABLE)
        assertThat(r.attempts).isEmpty()
        assertThat(wm.allServeEvents).isEmpty()
        assertThat(rows(r.callId.value).single()["outcome"]).isEqualTo("no-model-available")
        assertThat(rows(r.callId.value).single()["slot"]).isEqualTo("vision")
        assertThat(attemptRows(r.callId.value)).isEmpty()
        assertThat(finished(r.callId.value).single().outcome).isEqualTo(ModelCallOutcome.NO_MODEL_AVAILABLE)
    }

    @Test
    fun `empty image slot fails with no model available and zero attempts (AC-223)`() {
        val o = owner()
        val r = call(o, custom(o), ModelSlotKind.IMAGE, SlotRequest.Image(promptText)) as ProfileCallResult.Failed
        assertThat(r.reason).isEqualTo(SlotFailure.NO_MODEL_AVAILABLE)
        assertThat(r.attempts).isEmpty()
        assertThat(wm.allServeEvents).isEmpty()
    }

    @Test
    fun `every chain model missing - no model available, one missing attempt each, no provider call (AC-223)`() {
        val o = owner()
        val ref = custom(o, listOf(gone, ModelId("it/gone2")))
        val r = call(o, ref) as ProfileCallResult.Failed
        assertThat(r.reason).isEqualTo(SlotFailure.NO_MODEL_AVAILABLE)
        assertThat(r.attempts).containsExactly(
            Attempt(gone, AttemptOutcome.MISSING),
            Attempt(ModelId("it/gone2"), AttemptOutcome.MISSING),
        )
        assertThat(wm.allServeEvents).isEmpty()
        assertThat(attemptRows(r.callId.value)).hasSize(2)
        assertThat(rows(r.callId.value).single()["outcome"]).isEqualTo("no-model-available")
        assertThat(rows(r.callId.value).single()["fallback"]).isEqualTo(true)
    }

    @Test
    fun `main model missing then backup fails - fallback is recorded as true (AC-229)`() {
        val o = owner()
        val ref = custom(o, listOf(gone, b))
        stub(b, err(500))
        val r = call(o, ref) as ProfileCallResult.Failed
        assertThat(r.attempts).containsExactly(
            Attempt(gone, AttemptOutcome.MISSING),
            Attempt(b, AttemptOutcome.PROVIDER_ERROR),
        )
        assertThat(rows(r.callId.value).single()["fallback"]).isEqualTo(true)
        assertThat(finished(r.callId.value).single().fallback).isTrue()
    }

    @Test
    fun `single model failing is not a fallback (AC-229)`() {
        val o = owner()
        val ref = custom(o, listOf(a))
        stub(a, err(500))
        val r = call(o, ref) as ProfileCallResult.Failed
        assertThat(rows(r.callId.value).single()["fallback"]).isEqualTo(false)
    }

    @Test
    fun `ModelCallFinished reaches an application module listener and its publication completes (AC-229)`() {
        val o = owner()
        val ref = custom(o)
        stub(a, ok())
        val r = call(o, ref) as ProfileCallResult.Answered
        await().atMost(Duration.ofSeconds(5)).until { received.any { it.callId == r.callId } }
        await().atMost(Duration.ofSeconds(5)).until { completedPublications(r.callId.value) == 1 }
    }

    @Test
    fun `an incomplete ModelCallFinished publication is republished and completes (AC-229)`() {
        val o = owner()
        stub(a, ok())
        val r = call(o, custom(o)) as ProfileCallResult.Answered
        await().atMost(Duration.ofSeconds(5)).until { completedPublications(r.callId.value) == 1 }
        val before = received.count { it.callId == r.callId }
        jdbc.update(
            "UPDATE event_publication SET completion_date = NULL WHERE serialized_event LIKE ?",
            like(r.callId.value),
        )
        incomplete.resubmitIncompletePublications { (it.event as? ModelCallFinished)?.callId == r.callId }
        await().atMost(Duration.ofSeconds(5)).until { completedPublications(r.callId.value) == 1 }
        assertThat(received.count { it.callId == r.callId }).isEqualTo(before + 1)
    }

    /** Listener threads log while a test reads; doAppend is synchronized on the appender, so snapshot under it. */
    private fun logLines() = synchronized(logs) { logs.list.toList() }

    private fun like(callId: UUID) = "%$callId%"

    private fun completedPublications(callId: UUID) =
        jdbc.queryForObject(
            "SELECT count(*) FROM event_publication WHERE event_type LIKE '%ModelCallFinished' " +
                "AND serialized_event LIKE ? AND completion_date IS NOT NULL",
            Int::class.java,
            like(callId),
        )

    @Test
    fun `vision slot answers text and image slot answers bytes through their own models (AC-224)`() {
        val o = owner()
        val ref = custom(o, vision = listOf(ModelId("it/vision")), image = listOf(ModelId("it/image")))
        stub(ModelId("it/vision"), ok())
        val url = "data:image/png;base64," + Base64.getEncoder().encodeToString(png)
        stub(
            ModelId("it/image"),
            aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"",
                "images":[{"type":"image_url","image_url":{"url":"$url"}}]}}]}""",
            ),
        )
        val vision =
            SlotRequest.Vision(
                listOf(ChatMessage("user", promptText)),
                listOf(telex.llm.ImageInput(png, "image/png")),
            )
        val v = call(o, ref, ModelSlotKind.VISION, vision) as ProfileCallResult.Answered
        assertThat(v.answer).isEqualTo(SlotAnswer.Text(answerText))
        val i = call(o, ref, ModelSlotKind.IMAGE, SlotRequest.Image(promptText)) as ProfileCallResult.Answered
        assertThat((i.answer as SlotAnswer.Image).bytes).isEqualTo(png)
        assertThat(rows(i.callId.value).single()["slot"]).isEqualTo("image")
    }

    @Test
    fun `system profile slot with no catalog model fails without borrowing another slot (AC-223)`() {
        val o = owner()
        (catalog as SwitchableCatalog).let { c ->
            c.current = c.current.copy(models = c.current.models - ModelId("it/image"))
        }
        val r = call(o, balanced, ModelSlotKind.IMAGE, SlotRequest.Image(promptText)) as ProfileCallResult.Failed
        assertThat(r.reason).isEqualTo(SlotFailure.NO_MODEL_AVAILABLE)
        assertThat(r.attempts).containsExactly(Attempt(ModelId("it/image"), AttemptOutcome.MISSING))
        assertThat(wm.allServeEvents).isEmpty()
    }

    @Test
    fun `another Owner's custom profile id is not found, with no record and no event (AC-222)`() {
        val mine = owner()
        val theirs = owner()
        val foreign = custom(theirs)
        stub(a, ok())
        val r = call(mine, foreign)
        r as ProfileCallResult.Failed
        assertThat(r.reason).isEqualTo(SlotFailure.PROFILE_NOT_FOUND)
        assertThat(r.attempts).isEmpty()
        assertThat(rows(r.callId.value)).isEmpty()
        assertThat(
            jdbc.queryForObject("SELECT count(*) FROM model_call WHERE owner_id = ?", Int::class.java, mine.value),
        ).isZero()
        assertThat(finished(r.callId.value)).isEmpty()
        assertThat(wm.allServeEvents).isEmpty()
        val unknown = call(mine, ProfileRef.Custom(ModelProfileId(Uuid7.next())))
        assertThat((unknown as ProfileCallResult.Failed).reason).isEqualTo(SlotFailure.PROFILE_NOT_FOUND)
    }

    @Test
    fun `a failing record write is logged, the answer is returned and no event is published (ADR-0004)`() {
        val o = owner()
        val ref = custom(o)
        stub(a, ok())
        jdbc.execute(
            "CREATE FUNCTION it_fail_call() RETURNS trigger AS " +
                "\$\$ BEGIN RAISE EXCEPTION 'it forced failure'; END \$\$ LANGUAGE plpgsql",
        )
        jdbc.execute(
            "CREATE TRIGGER it_fail_call BEFORE INSERT ON model_call FOR EACH ROW EXECUTE FUNCTION it_fail_call()",
        )
        val r = call(o, ref)
        assertThat(r).isInstanceOf(ProfileCallResult.Answered::class.java)
        assertThat((r as ProfileCallResult.Answered).answer).isEqualTo(SlotAnswer.Text(answerText))
        assertThat(jdbc.queryForObject("SELECT count(*) FROM model_call WHERE owner_id = ?", Int::class.java, o.value))
            .isZero()
        assertThat(events.stream(ModelCallFinished::class.java).filter { it.ownerId == o }.toList()).isEmpty()
        assertThat(logLines().any { it.level.isGreaterOrEqual(Level.WARN) && it.loggerName.startsWith("telex.agents") })
            .isTrue()
    }

    @Test
    fun `no request or answer text reaches the records, the event or the logs (AC-229)`() {
        val o = owner()
        val ref = custom(o)
        stub(a, err(429))
        stub(b, ok())
        val r = call(o, ref) as ProfileCallResult.Answered
        val dump =
            rows(r.callId.value).toString() + attemptRows(r.callId.value) + finished(r.callId.value) +
                logLines().joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message ?: "") }
        assertThat(dump).doesNotContain(promptText).doesNotContain(answerText).doesNotContain("sk-or-it-key")
        val columns =
            jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name IN " +
                    "('model_call','model_call_attempt')",
                String::class.java,
            )
        assertThat(columns.filterNotNull()).noneMatch {
            it.contains("text") || it.contains("content") ||
                it.contains("prompt")
        }
    }

    @Test
    fun `calls and attempts are counted without owner ids or content in tags`() {
        val o = owner()
        val ref = custom(o)
        stub(a, err(429))
        stub(b, ok())
        val calls0 =
            meters
                .find("telex.model.calls")
                .tags("slot", "text", "outcome", "answered", "fallback", "true")
                .counter()
                ?.count() ?: 0.0
        val timer0 =
            meters
                .find("telex.model.attempt")
                .tag("outcome", "rate-limited")
                .timer()
                ?.count() ?: 0L
        call(o, ref)
        val counter =
            meters.get("telex.model.calls").tags("slot", "text", "outcome", "answered", "fallback", "true").counter()
        assertThat(counter.count()).isEqualTo(calls0 + 1)
        assertThat(
            meters
                .get("telex.model.attempt")
                .tag("outcome", "rate-limited")
                .timer()
                .count(),
        ).isEqualTo(timer0 + 1)
        val tagValues =
            meters.meters.filter { it.id.name.startsWith("telex.model.") }.flatMap { m ->
                m.id.tags.map { it.value }
            }
        assertThat(tagValues).noneMatch { it.contains(o.value.toString()) || it.contains(promptText) }
    }
}
