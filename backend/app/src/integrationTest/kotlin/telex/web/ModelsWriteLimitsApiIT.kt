package telex.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionTemplate
import telex.TestcontainersConfiguration
import telex.agents.ModelProfileId
import telex.agents.ModelSlotKind
import telex.agents.internal.profile.ModelProfile
import telex.agents.internal.profile.ProfileRepository
import telex.identity.OwnerId
import telex.identity.SignInSessions
import telex.identity.internal.owner.Owners
import telex.llm.CatalogModel
import telex.llm.CatalogSnapshot
import telex.llm.CatalogState
import telex.llm.Modality
import telex.llm.ModelCatalog
import telex.llm.ModelId
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** T24 (review-2026-10-03): name rule, default-vs-delete race, request bounds, saves during an outage. */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = ["telex.llm.openrouter.api-key=sk-or-it-key"],
)
@Import(TestcontainersConfiguration::class, ModelsWriteLimitsApiIT.MutableCatalog::class)
class ModelsWriteLimitsApiIT(
    @LocalServerPort private val port: Int,
) {
    class SwitchableCatalog : ModelCatalog {
        @Volatile var current: CatalogSnapshot = CatalogSnapshot(emptyMap(), null, null, CatalogState.NOT_LOADED)

        override fun snapshot() = current

        override fun find(modelId: ModelId) = current.models[modelId]
    }

    @TestConfiguration
    class MutableCatalog {
        @Bean
        @Primary
        fun switchableCatalog(): ModelCatalog = SwitchableCatalog()
    }

    @Autowired lateinit var catalog: ModelCatalog

    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var profiles: ProfileRepository

    @Autowired lateinit var transactions: TransactionTemplate

    private val http = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private val text = setOf(Modality.TEXT)

    private fun model(
        id: String,
        name: String,
        takes: Set<Modality>,
        produces: Set<Modality>,
        input: String? = null,
        output: String? = null,
        perImage: String? = null,
    ) = CatalogModel(
        ModelId(id),
        name,
        "prov",
        takes,
        produces,
        input?.let(::BigDecimal),
        output?.let(::BigDecimal),
        perImage?.let(::BigDecimal),
        128_000,
    )

    private val fixtures =
        listOf(
            model("it/main", "Zeta main", text, text, "1.00", "2.00"),
            model("it/back", "Alpha back", text, text, "10.00", "20.00"),
        )

    private fun load(
        state: CatalogState,
        models: List<CatalogModel> = fixtures,
    ) {
        (catalog as SwitchableCatalog).current =
            CatalogSnapshot(models.associateBy { it.modelId }, Instant.now(), null, state)
    }

    @BeforeEach
    fun loaded() = load(CatalogState.CURRENT)

    private class Signed(
        val owner: OwnerId,
        val key: String,
    )

    private fun signedIn(): Signed {
        val email = "${UUID.randomUUID()}@mail.com"
        val owner = owners.findOrCreate(email, email, Instant.now()).first
        return Signed(owner, sessions.start(owner, null, "Mozilla/5.0 Firefox/130.0", "Europe/Kyiv", false).key)
    }

    private fun stored(
        o: OwnerId,
        name: String,
        text: List<String> = listOf("it/own"),
        vision: List<String> = emptyList(),
    ): ModelProfileId {
        val id = ModelProfileId(UUID.randomUUID())
        profiles.insert(
            o,
            ModelProfile(
                id,
                name,
                mapOf(
                    ModelSlotKind.TEXT to text.map(::ModelId),
                    ModelSlotKind.VISION to vision.map(::ModelId),
                    ModelSlotKind.IMAGE to emptyList(),
                ),
            ),
            Instant.now(),
        )
        return id
    }

    private fun call(
        method: String,
        path: String,
        key: String?,
        requestBody: String? = null,
        xsrf: Boolean = true,
    ): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
        val cookies = listOfNotNull("XSRF-TOKEN=csrf", key?.let { "telex_session=$it" }).joinToString("; ")
        b.header("Cookie", cookies)
        val headers = mutableMapOf<String, String>()
        if (xsrf) {
            b.header("X-XSRF-TOKEN", "csrf")
            headers["X-XSRF-TOKEN"] = "csrf"
        }
        if (requestBody != null) {
            b.header("Content-Type", "application/json")
            headers["Content-Type"] = "application/json"
        }
        b.method(
            method,
            requestBody?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody(),
        )
        val response = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        ContractValidator.assertConforms(
            method,
            path,
            requestBody,
            headers,
            response,
            ContractValidator.MODEL_PROFILES_SPEC,
        )
        return response
    }

    private fun body(r: HttpResponse<String>): JsonNode = json.readTree(r.body())

    private fun JsonNode.each(): List<JsonNode> = (0 until size()).map { get(it) }

    private fun slots(
        text: List<String>,
        vision: List<String> = emptyList(),
        image: List<String> = emptyList(),
    ): String {
        fun arr(l: List<String>) = l.joinToString(",", "[", "]") { "\"$it\"" }
        return """{"text":${arr(text)},"vision":${arr(vision)},"image":${arr(image)}}"""
    }

    private fun profileJson(
        name: String,
        slots: String = slots(listOf("it/main")),
        from: String? = null,
    ): String = """{"name":"$name","slots":$slots${from?.let { ""","duplicatedFrom":$it""" } ?: ""}}"""

    private fun customRef(id: ModelProfileId) = """{"kind":"custom","id":"${id.value}"}"""

    private fun create(
        s: Signed,
        name: String,
        slots: String = slots(listOf("it/main")),
        from: String? = null,
    ) = call("POST", "/api/v1/models/profiles", s.key, profileJson(name, slots, from))

    private fun assertProblem(
        r: HttpResponse<String>,
        status: Int,
        code: String,
    ) {
        assertThat(r.statusCode()).isEqualTo(status)
        assertThat(r.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json")
        val b = body(r)
        assertThat(b["type"].asString()).isEqualTo("urn:telex:error:$code")
        assertThat(b["code"].asString()).isEqualTo(code)
        assertThat(b["status"].asInt()).isEqualTo(status)
    }

    /** A 400 validation-failed whose errors[] holds exactly these (field, code) pairs. */
    private fun assertInvalid(
        r: HttpResponse<String>,
        vararg expected: Pair<String, String>,
    ) {
        assertProblem(r, 400, "validation-failed")
        val actual = body(r)["errors"].each().map { it["field"].asString() to it["code"].asString() }
        assertThat(actual).containsExactlyInAnyOrder(*expected)
    }

    private fun list(s: Signed): JsonNode = body(call("GET", "/api/v1/models/profiles", s.key))

    private fun names(s: Signed) = list(s)["items"].each().map { it["name"].asString() }

    // ---- T24: review-2026-10-03 D2 / E3 / E4 / A4a

    @Test
    fun `system keys are fine as custom names while the display names stay refused (AC-214)`() {
        val s = signedIn()
        val fast = create(s, "Fast")
        assertThat(fast.statusCode()).describedAs(fast.body()).isEqualTo(201)
        assertInvalid(create(s, "careful"), "name" to "name-reserved")
        assertInvalid(create(s, "Fast and cheap"), "name" to "name-reserved")
    }

    @Test
    fun `a default racing a delete of the same profile is 404 not-found, never 500 (AC-220, AC-222)`() {
        val s = signedIn()
        val id = stored(s.owner, "Doomed")
        val locked = java.util.concurrent.CountDownLatch(1)
        val deleter =
            Executors.newSingleThreadExecutor().submit {
                transactions.executeWithoutResult {
                    profiles.lockOwner(s.owner)
                    locked.countDown()
                    Thread.sleep(1_500)
                    profiles.delete(s.owner, id)
                }
            }
        locked.await()
        val r = call("PUT", "/api/v1/models/default-profile", s.key, """{"profile":${customRef(id)}}""")
        deleter.get()
        assertProblem(r, 404, "not-found")
        assertThat(list(s)["defaultProfile"]["key"].asString()).isEqualTo("balanced")
    }

    @Test
    fun `an over-long model id is 400 validation-failed and the id is not echoed back`() {
        val s = signedIn()
        val long = "x".repeat(201)
        val r = create(s, "Long id", slots(listOf(long)))
        assertProblem(r, 400, "validation-failed")
        assertThat(r.body()).doesNotContain(long)
        assertThat(body(r)["errors"].each().map { it["field"].asString() }).containsExactly("slots.text[0]")
        val put =
            call(
                "PUT",
                "/api/v1/models/profiles/${stored(s.owner, "Own").value}",
                s.key,
                profileJson("Own", slots(listOf(long))),
            )
        assertProblem(put, 400, "validation-failed")
    }

    @Test
    fun `an over-long name and an oversized slot array are 400 validation-failed`() {
        val s = signedIn()
        assertInvalid(create(s, "n".repeat(201)), "name" to "size")
        val many = List(11) { "it/m$it" }
        assertInvalid(create(s, "Many", slots(many)), "slots.text" to "size")
        assertThat(names(s)).doesNotContain("Many")
    }

    @Test
    fun `create and update succeed while the catalog is UPDATE_FAILED with a snapshot (AC-212)`() {
        val s = signedIn()
        load(CatalogState.UPDATE_FAILED)
        val created = create(s, "During outage")
        assertThat(created.statusCode()).isEqualTo(201)
        val id = body(created)["ref"]["id"].asString()
        val put =
            call("PUT", "/api/v1/models/profiles/$id", s.key, profileJson("Still saving", slots(listOf("it/back"))))
        assertThat(put.statusCode()).isEqualTo(200)
        assertThat(body(put)["name"].asString()).isEqualTo("Still saving")
    }
}
