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
import org.springframework.jdbc.core.JdbcTemplate
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

/**
 * T14 read endpoints over HTTP, against `model-profiles/contracts/openapi.yaml`:
 * AC-10, AC-211, AC-212, AC-218, AC-222, AC-226.
 */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "telex.llm.openrouter.api-key=sk-or-it-key",
        "telex.models.system-profiles.balanced.text=it/missing,it/back,it/main",
        "telex.models.system-profiles.balanced.vision=it/vision",
        "telex.models.system-profiles.balanced.image=it/image",
        "telex.models.system-profiles.fast.text=it/main",
    ],
)
@Import(TestcontainersConfiguration::class, ModelsReadApiIT.MutableCatalog::class)
class ModelsReadApiIT(
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

    @Autowired lateinit var jdbc: JdbcTemplate

    private val http = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()
    private val refreshed = Instant.parse("2026-10-03T08:00:00Z")
    private val failed = Instant.parse("2026-10-03T09:00:00Z")
    private val text = setOf(Modality.TEXT)

    private fun model(
        id: String,
        name: String,
        takes: Set<Modality>,
        produces: Set<Modality>,
        input: String? = null,
        output: String? = null,
        perImage: String? = null,
        context: Int? = 128_000,
    ) = CatalogModel(
        ModelId(id),
        name,
        "prov",
        takes,
        produces,
        input?.let(::BigDecimal),
        output?.let(::BigDecimal),
        perImage?.let(::BigDecimal),
        context,
    )

    private val fixtures =
        listOf(
            model("it/main", "Zeta main", text, text, "1.00", "2.00"),
            model("it/back", "Alpha back", text, text, "10.00", "20.00"),
            model("it/vision", "Mid vision", setOf(Modality.TEXT, Modality.IMAGE), text, "0.50", "1.00"),
            model("it/image", "Draw", text, setOf(Modality.IMAGE), perImage = "0.04", context = null),
            model("it/own", "Own text", text, text, "1.00", "2.00"),
            model("it/none", "Fits nothing", setOf(Modality.IMAGE), emptySet()),
        )

    private fun load(
        state: CatalogState,
        models: List<CatalogModel> = fixtures,
        refreshedAt: Instant? = refreshed,
        failedAt: Instant? = null,
    ) {
        (catalog as SwitchableCatalog).current =
            CatalogSnapshot(models.associateBy { it.modelId }, refreshedAt, failedAt, state)
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

    private fun custom(
        o: OwnerId,
        name: String,
        text: List<String> = listOf("it/own"),
    ): ModelProfileId {
        val id = ModelProfileId(UUID.randomUUID())
        profiles.insert(
            o,
            ModelProfile(
                id,
                name,
                mapOf(
                    ModelSlotKind.TEXT to text.map(::ModelId),
                    ModelSlotKind.VISION to emptyList(),
                    ModelSlotKind.IMAGE to emptyList(),
                ),
            ),
            Instant.now(),
        )
        return id
    }

    private fun get(
        path: String,
        key: String?,
    ): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).header("X-Telex-Background", "1")
        key?.let { b.header("Cookie", "telex_session=$it") }
        val response = http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString())
        ContractValidator.assertConforms(
            "GET",
            path,
            null,
            mapOf("X-Telex-Background" to "1"),
            response,
            ContractValidator.MODEL_PROFILES_SPEC,
            // The validator parses a oneOf-typed query parameter (`from`: system key | uuid) as JSON and so rejects
            // the bare value the contract describes; the response is still fully validated.
            setOf("validation.request.parameter.schema.invalidJson"),
        )
        return response
    }

    private fun body(r: HttpResponse<String>): JsonNode = json.readTree(r.body())

    private fun JsonNode.each(): List<JsonNode> = (0 until size()).map { get(it) }

    private fun JsonNode.texts(): List<String> = each().map { it.asString() }

    private fun JsonNode.field(name: String): List<String> = each().map { it[name].asString() }

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

    private fun item(
        list: JsonNode,
        name: String,
    ): JsonNode = list["items"].each().first { it["name"].asString() == name }

    // ---- 401 on every read

    @Test
    fun `every read endpoint without a session is 401 unauthenticated`() {
        val ids = UUID.randomUUID()
        listOf(
            "/api/v1/models/catalog",
            "/api/v1/models/profiles",
            "/api/v1/models/profiles/balanced",
            "/api/v1/models/profiles/$ids",
            "/api/v1/models/profile-draft",
        ).forEach { assertProblem(get(it, null), 401, "unauthenticated") }
    }

    // ---- AC-211 / AC-212 catalog

    @Test
    fun `catalog is camelCase, ordered by name, slot-fit only, prices as decimal strings (AC-211)`() {
        val r = get("/api/v1/models/catalog", signedIn().key)
        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.headers().firstValue("Content-Type").orElse("")).startsWith("application/json")
        val b = body(r)
        assertThat(b["state"].asString()).isEqualTo("current")
        assertThat(Instant.parse(b["lastRefreshedAt"].asString())).isEqualTo(refreshed)
        assertThat(b["lastFailedAt"].isNull).isTrue()
        assertThat(b["models"].field("name"))
            .containsExactly("Alpha back", "Draw", "Mid vision", "Own text", "Zeta main")
        val vision = b["models"].each().first { it["modelId"].asString() == "it/vision" }
        assertThat(vision["provider"].asString()).isEqualTo("prov")
        assertThat(vision["takes"].texts()).containsExactlyInAnyOrder("text", "image")
        assertThat(vision["produces"].texts()).containsExactly("text")
        assertThat(vision["slots"].texts()).containsExactlyInAnyOrder("text", "vision")
        assertThat(vision["inputPricePerMillionTokens"].isString).isTrue()
        assertThat(BigDecimal(vision["inputPricePerMillionTokens"].asString())).isEqualByComparingTo("0.5")
        assertThat(BigDecimal(vision["outputPricePerMillionTokens"].asString())).isEqualByComparingTo("1")
        assertThat(vision["pricePerImage"].isNull).isTrue()
        assertThat(vision["contextLength"].asInt()).isEqualTo(128_000)
        val image = b["models"].each().first { it["modelId"].asString() == "it/image" }
        assertThat(image["slots"].texts()).containsExactly("image")
        assertThat(image["inputPricePerMillionTokens"].isNull).isTrue()
        assertThat(BigDecimal(image["pricePerImage"].asString())).isEqualByComparingTo("0.04")
        assertThat(image["contextLength"].isNull).isTrue()
    }

    @Test
    fun `a failed refresh answers 200 with the last list, state update-failed and both times (AC-212)`() {
        load(CatalogState.UPDATE_FAILED, failedAt = failed)
        val r = get("/api/v1/models/catalog", signedIn().key)
        assertThat(r.statusCode()).isEqualTo(200)
        val b = body(r)
        assertThat(b["state"].asString()).isEqualTo("update-failed")
        assertThat(Instant.parse(b["lastRefreshedAt"].asString())).isEqualTo(refreshed)
        assertThat(Instant.parse(b["lastFailedAt"].asString())).isEqualTo(failed)
        assertThat(b["models"]).hasSize(5)
    }

    @Test
    fun `never loaded catalog is 200 not-loaded, empty, and system slots have no model (AC-212)`() {
        load(CatalogState.NOT_LOADED, emptyList(), refreshedAt = null, failedAt = failed)
        val s = signedIn()
        val b = body(get("/api/v1/models/catalog", s.key))
        assertThat(b["state"].asString()).isEqualTo("not-loaded")
        assertThat(b["lastRefreshedAt"].isNull).isTrue()
        assertThat(b["models"]).isEmpty()
        val list = body(get("/api/v1/models/profiles", s.key))
        listOf("Fast and cheap", "Balanced", "Careful").forEach {
            assertThat(item(list, it)["slots"]["text"]["state"].asString()).isEqualTo("no-model-available")
        }
    }

    // ---- AC-226

    @Test
    fun `without a provider key the catalog is not-configured, the list flags it and drafts are 409 (AC-226)`() {
        load(CatalogState.NOT_CONFIGURED, emptyList(), refreshedAt = null)
        val s = signedIn()
        assertThat(body(get("/api/v1/models/catalog", s.key))["state"].asString()).isEqualTo("not-configured")
        val list = body(get("/api/v1/models/profiles", s.key))
        assertThat(list["aiConfigured"].asBoolean()).isFalse()
        assertThat(list["items"].each().take(3).map { it["name"].asString() })
            .containsExactly("Fast and cheap", "Balanced", "Careful")
        assertProblem(get("/api/v1/models/profile-draft", s.key), 409, "ai-not-configured")
        assertProblem(get("/api/v1/models/profile-draft?from=balanced", s.key), 409, "ai-not-configured")
    }

    // ---- list, AC-10

    @Test
    fun `list has the contract shape, system profiles first then own, tagged refs, whole-cent prices (AC-10)`() {
        val s = signedIn()
        val mine = custom(s.owner, "Night shift")
        val r = get("/api/v1/models/profiles", s.key)
        assertThat(r.statusCode()).isEqualTo(200)
        val b = body(r)
        assertThat(b["aiConfigured"].asBoolean()).isTrue()
        assertThat(b["customProfileLimit"].asInt()).isEqualTo(20)
        assertThat(b["defaultProfile"]["kind"].asString()).isEqualTo("system")
        assertThat(b["defaultProfile"]["key"].asString()).isEqualTo("balanced")
        assertThat(b["items"].field("name"))
            .containsExactly("Fast and cheap", "Balanced", "Careful", "Night shift")
        assertThat(b["items"][0]["ref"]["kind"].asString()).isEqualTo("system")
        assertThat(b["items"][0]["ref"]["key"].asString()).isEqualTo("fast")
        val own = item(b, "Night shift")
        assertThat(own["ref"]["kind"].asString()).isEqualTo("custom")
        assertThat(own["ref"]["id"].asString()).isEqualTo(mine.value.toString())
        assertThat(own["choosable"].asBoolean()).isTrue()
        assertThat(own["slots"]["image"]["state"].asString()).isEqualTo("not-used")
        assertThat(own["slots"]["image"]["currentModelId"].isNull).isTrue()
        assertThat(own["slots"]["image"]["chain"]).isEmpty()
        assertThat(own["pricePer100Runs"]["state"].asString()).isIn("estimate", "under-one-cent")
        // Balanced: main model "it/missing" is not in the catalog, so the text slot falls back to it/back.
        val text = item(b, "Balanced")["slots"]["text"]
        assertThat(text["state"].asString()).isEqualTo("fallback")
        assertThat(text["currentModelId"].asString()).isEqualTo("it/back")
        assertThat(text["chain"].field("modelId")).containsExactly("it/missing", "it/back", "it/main")
        val missing = text["chain"].each().first { it["modelId"].asString() == "it/missing" }
        assertThat(missing["availability"].asString()).isEqualTo("not-in-catalog")
        assertThat(missing["name"].isNull).isTrue()
        val price = item(b, "Balanced")["pricePer100Runs"]
        assertThat(price["state"].asString()).isEqualTo("estimate")
        assertThat(price["amount"].isString).isTrue()
        assertThat(price["amount"].asString()).matches("\\d+\\.\\d{2}")
        assertThat(item(b, "Balanced")["slots"]["vision"]["state"].asString()).isEqualTo("main-model")
    }

    @Test
    fun `the list never shows another Owner's profile (AC-222)`() {
        val a = signedIn()
        val b = signedIn()
        custom(a.owner, "Night shift")
        val names = body(get("/api/v1/models/profiles", b.key))["items"].field("name")
        assertThat(names).doesNotContain("Night shift").hasSize(3)
    }

    // ---- one profile

    @Test
    fun `a system profile is read by key, an own profile by id (AC-10)`() {
        val s = signedIn()
        val id = custom(s.owner, "Night shift")
        val sys = get("/api/v1/models/profiles/balanced", s.key)
        assertThat(sys.statusCode()).isEqualTo(200)
        assertThat(body(sys)["ref"]["key"].asString()).isEqualTo("balanced")
        assertThat(body(sys)["slots"]["text"]["state"].asString()).isEqualTo("fallback")
        val own = get("/api/v1/models/profiles/${id.value}", s.key)
        assertThat(own.statusCode()).isEqualTo(200)
        val b = body(own)
        assertThat(b["ref"]["id"].asString()).isEqualTo(id.value.toString())
        assertThat(b["name"].asString()).isEqualTo("Night shift")
        assertThat(b["slots"]["text"]["chain"][0]["name"].asString()).isEqualTo("Own text")
    }

    @Test
    fun `another Owner's profile, an unknown uuid and a malformed key all give identical 404 not-found (AC-222)`() {
        val a = signedIn()
        val b = signedIn()
        val foreign = custom(a.owner, "Night shift")
        val responses =
            listOf(
                get("/api/v1/models/profiles/${foreign.value}", b.key),
                get("/api/v1/models/profiles/${UUID.randomUUID()}", b.key),
                get("/api/v1/models/profiles/not-a-key", b.key),
            )
        responses.forEach { assertProblem(it, 404, "not-found") }
        val shapes =
            responses.map { r ->
                val n = body(r)
                n
                    .propertyNames()
                    .asSequence()
                    .filter { it != "instance" }
                    .sorted()
                    .joinToString { "$it=${n[it]}" }
            }
        assertThat(shapes.toSet()).hasSize(1)
        assertThat(responses[0].body()).doesNotContain("Night shift")
    }

    // ---- draft

    @Test
    fun `a draft without a source is empty and a draft from a system profile is its copy (AC-218 happy side)`() {
        val s = signedIn()
        val empty = body(get("/api/v1/models/profile-draft", s.key))
        assertThat(empty["name"].asString()).isEmpty()
        assertThat(empty["duplicatedFrom"]?.isNull ?: true).isTrue()
        assertThat(empty["slots"]["text"]["chain"]).isEmpty()
        val copy = get("/api/v1/models/profile-draft?from=balanced", s.key)
        assertThat(copy.statusCode()).isEqualTo(200)
        val b = body(copy)
        assertThat(b["name"].asString()).isEqualTo("Balanced copy")
        assertThat(b["duplicatedFrom"]["kind"].asString()).isEqualTo("system")
        assertThat(b["duplicatedFrom"]["key"].asString()).isEqualTo("balanced")
        assertThat(b["slots"]["text"]["chain"].field("modelId"))
            .containsExactly("it/missing", "it/back", "it/main")
        assertThat(b["pricePer100Runs"]["state"].asString()).isEqualTo("estimate")
    }

    @Test
    fun `a draft from another Owner's or an unknown or malformed profile is 404 not-found (AC-222)`() {
        val a = signedIn()
        val b = signedIn()
        val foreign = custom(a.owner, "Night shift")
        listOf(foreign.value.toString(), UUID.randomUUID().toString(), "not-a-key").forEach {
            assertProblem(get("/api/v1/models/profile-draft?from=$it", b.key), 404, "not-found")
        }
    }

    @Test
    fun `with 20 custom profiles the draft is 409 profile-limit-reached, with 19 it opens (AC-218)`() {
        val s = signedIn()
        repeat(19) { custom(s.owner, "P$it") }
        assertThat(get("/api/v1/models/profile-draft", s.key).statusCode()).isEqualTo(200)
        custom(s.owner, "P19")
        assertProblem(get("/api/v1/models/profile-draft", s.key), 409, "profile-limit-reached")
        assertProblem(get("/api/v1/models/profile-draft?from=balanced", s.key), 409, "profile-limit-reached")
        assertThat(body(get("/api/v1/models/profiles", s.key))["items"]).hasSize(23)
    }
}
