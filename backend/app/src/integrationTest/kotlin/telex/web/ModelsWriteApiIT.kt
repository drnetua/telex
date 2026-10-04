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

/**
 * T15 write endpoints over HTTP, against `model-profiles/contracts/openapi.yaml`:
 * AC-51, AC-213..AC-217, AC-219, AC-220, AC-221, AC-223 (+ AC-218 / AC-222 / AC-226 on the write side).
 */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "telex.llm.openrouter.api-key=sk-or-it-key",
        "telex.models.system-profiles.balanced.text=it/missing,it/back,it/main",
        "telex.models.system-profiles.balanced.vision=it/vision",
        "telex.models.system-profiles.balanced.image=it/image",
        "telex.models.system-profiles.fast.text=it/main",
        "telex.models.system-profiles.careful.text=it/back",
    ],
)
@Import(TestcontainersConfiguration::class, ModelsWriteApiIT.MutableCatalog::class)
class ModelsWriteApiIT(
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
            model("it/vision", "Mid vision", setOf(Modality.TEXT, Modality.IMAGE), text, "0.50", "1.00"),
            model("it/image", "Draw", text, setOf(Modality.IMAGE), perImage = "0.04"),
            model("it/own", "Own text", text, text, "1.00", "2.00"),
            model("it/extra", "Extra text", text, text, "1.00", "2.00"),
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

    private val balancedRef = """{"kind":"system","key":"balanced"}"""

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

    // ---- 401 / XSRF / unknown property

    @Test
    fun `every write without a session is 401 unauthenticated`() {
        val id = UUID.randomUUID()
        assertProblem(call("POST", "/api/v1/models/profiles", null, profileJson("X")), 401, "unauthenticated")
        assertProblem(call("PUT", "/api/v1/models/profiles/$id", null, profileJson("X")), 401, "unauthenticated")
        assertProblem(call("DELETE", "/api/v1/models/profiles/$id", null), 401, "unauthenticated")
        val choice = """{"profile":$balancedRef}"""
        assertProblem(call("PUT", "/api/v1/models/default-profile", null, choice), 401, "unauthenticated")
    }

    @Test
    fun `every write without the XSRF header is 403 forbidden and changes nothing`() {
        val s = signedIn()
        val id = stored(s.owner, "Night shift")
        assertProblem(call("POST", "/api/v1/models/profiles", s.key, profileJson("X"), xsrf = false), 403, "forbidden")
        val put = call("PUT", "/api/v1/models/profiles/${id.value}", s.key, profileJson("Renamed"), xsrf = false)
        assertProblem(put, 403, "forbidden")
        assertProblem(call("DELETE", "/api/v1/models/profiles/${id.value}", s.key, xsrf = false), 403, "forbidden")
        val choice = """{"profile":${customRef(id)}}"""
        assertProblem(call("PUT", "/api/v1/models/default-profile", s.key, choice, xsrf = false), 403, "forbidden")
        assertThat(names(s)).contains("Night shift").doesNotContain("X", "Renamed")
        assertThat(list(s)["defaultProfile"]["key"].asString()).isEqualTo("balanced")
    }

    @Test
    fun `an unknown JSON property is 400 validation-failed on every write`() {
        val s = signedIn()
        val id = stored(s.owner, "Night shift")
        val extra = """{"name":"X","slots":${slots(listOf("it/main"))},"colour":"red"}"""
        assertProblem(call("POST", "/api/v1/models/profiles", s.key, extra), 400, "validation-failed")
        assertProblem(call("PUT", "/api/v1/models/profiles/${id.value}", s.key, extra), 400, "validation-failed")
        val choice = """{"profile":$balancedRef,"colour":"red"}"""
        assertProblem(call("PUT", "/api/v1/models/default-profile", s.key, choice), 400, "validation-failed")
        assertThat(names(s)).doesNotContain("X")
    }

    @Test
    fun `a profile reference that mixes variants or has unknown properties is 400 validation-failed`() {
        val s = signedIn()
        val id = stored(s.owner, "Night shift")
        val malformed =
            listOf(
                """{"kind":"system","key":"balanced","id":"${id.value}"}""",
                """{"kind":"custom","id":"${id.value}","key":"balanced"}""",
                """{"kind":"system"}""",
                """{"kind":"custom"}""",
                """{"kind":"other","key":"balanced"}""",
                """{"kind":"system","key":"balanced","colour":"red"}""",
            )
        malformed.forEach { ref ->
            val choice = """{"profile":$ref}"""
            assertProblem(call("PUT", "/api/v1/models/default-profile", s.key, choice), 400, "validation-failed")
            val copy = """{"name":"X","slots":${slots(listOf("it/main"))},"duplicatedFrom":$ref}"""
            assertProblem(call("POST", "/api/v1/models/profiles", s.key, copy), 400, "validation-failed")
        }
        assertThat(names(s)).doesNotContain("X")
        assertThat(list(s)["defaultProfile"]["key"].asString()).isEqualTo("balanced")
    }

    // ---- create, AC-213

    @Test
    fun `duplicating Balanced is 201 with Location, models missing from the catalog kept (AC-213)`() {
        val s = signedIn()
        val r =
            create(
                s,
                "  Cheap vision  ",
                slots(listOf("it/missing", "it/main"), listOf("it/vision")),
                from = balancedRef,
            )
        assertThat(r.statusCode()).isEqualTo(201)
        assertThat(r.headers().firstValue("Content-Type").orElse("")).startsWith("application/json")
        val b = body(r)
        val id = b["ref"]["id"].asString()
        assertThat(b["ref"]["kind"].asString()).isEqualTo("custom")
        assertThat(r.headers().firstValue("Location").orElse("")).endsWith("/api/v1/models/profiles/$id")
        assertThat(b["name"].asString()).isEqualTo("Cheap vision")
        assertThat(b["choosable"].asBoolean()).isTrue()
        val chain = b["slots"]["text"]["chain"].each()
        assertThat(chain.map { it["modelId"].asString() }).containsExactly("it/missing", "it/main")
        assertThat(chain[0]["availability"].asString()).isEqualTo("not-in-catalog")
        assertThat(chain[0]["name"].isNull).isTrue()
        assertThat(b["slots"]["text"]["currentModelId"].asString()).isEqualTo("it/main")
        assertThat(b["slots"]["vision"]["chain"][0]["modelId"].asString()).isEqualTo("it/vision")
        assertThat(b["slots"]["image"]["state"].asString()).isEqualTo("not-used")
        assertThat(b["pricePer100Runs"]["state"].asString()).isNotEmpty()
        val own = list(s)["items"].each().first { it["name"].asString() == "Cheap vision" }
        assertThat(own["ref"]["id"].asString()).isEqualTo(id)
        assertThat(call("GET", "/api/v1/models/profiles/$id", s.key).statusCode()).isEqualTo(200)
    }

    @Test
    fun `the chain order is the order sent, position 1 is the main model (AC-213)`() {
        val s = signedIn()
        val b = body(create(s, "Ordered", slots(listOf("it/back", "it/main"))))
        assertThat(b["slots"]["text"]["chain"].each().map { it["modelId"].asString() })
            .containsExactly("it/back", "it/main")
        assertThat(b["slots"]["text"]["currentModelId"].asString()).isEqualTo("it/back")
    }

    @Test
    fun `a model missing from the catalog is refused when it does not come from the source (AC-213, AC-221)`() {
        val s = signedIn()
        assertInvalid(
            create(s, "Fresh", slots(listOf("it/missing")), from = null),
            "slots.text[0]" to "model-left-catalog",
        )
        assertInvalid(
            create(s, "Fresh", slots(listOf("it/main", "it/gone")), from = balancedRef),
            "slots.text[1]" to "model-left-catalog",
        )
        assertThat(names(s)).doesNotContain("Fresh")
    }

    @Test
    fun `a duplicate from another Owner's or an unknown profile is 404 not-found (AC-222)`() {
        val a = signedIn()
        val b = signedIn()
        val foreign = stored(a.owner, "Night shift")
        assertProblem(create(b, "Copy", from = customRef(foreign)), 404, "not-found")
        assertProblem(create(b, "Copy", from = customRef(ModelProfileId(UUID.randomUUID()))), 404, "not-found")
        assertThat(names(b)).doesNotContain("Copy")
    }

    // ---- AC-214

    @Test
    fun `an empty or blank name is 400 name-required (AC-214)`() {
        val s = signedIn()
        assertInvalid(create(s, ""), "name" to "name-required")
        assertInvalid(create(s, "   "), "name" to "name-required")
    }

    @Test
    fun `a name over 40 characters after trimming is 400 name-too-long, exactly 40 is fine (AC-214)`() {
        val s = signedIn()
        assertInvalid(create(s, "x".repeat(41)), "name" to "name-too-long")
        assertInvalid(create(s, " " + "x".repeat(41) + " "), "name" to "name-too-long")
        assertThat(create(s, " " + "y".repeat(40) + " ").statusCode()).isEqualTo(201)
    }

    @Test
    fun `a name equal to one of my profiles ignoring case is 400 name-taken, another Owner's name is free (AC-214)`() {
        val a = signedIn()
        val b = signedIn()
        stored(a.owner, "Cheap vision")
        assertInvalid(create(a, "cheap VISION"), "name" to "name-taken")
        assertThat(create(b, "Cheap vision").statusCode()).isEqualTo(201)
    }

    @Test
    fun `a system profile name, in any case, is 400 name-reserved (AC-214)`() {
        val s = signedIn()
        assertInvalid(create(s, "Balanced"), "name" to "name-reserved")
        assertInvalid(create(s, "fast AND cheap"), "name" to "name-reserved")
    }

    @Test
    fun `two parallel creates of the same name give one 201 and one 400 name-taken (AC-214)`() {
        val s = signedIn()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(List(2) { Callable { create(s, "Racing") } }).map { it.get() }
            assertThat(results.map { it.statusCode() }).containsExactlyInAnyOrder(201, 400)
            assertInvalid(results.first { it.statusCode() == 400 }, "name" to "name-taken")
            assertThat(names(s).count { it == "Racing" }).isEqualTo(1)
        } finally {
            pool.shutdownNow()
        }
    }

    // ---- AC-215, AC-216, AC-217

    @Test
    fun `an empty text slot, or a body without slots text, is 400 text-slot-required (AC-215)`() {
        val s = signedIn()
        assertInvalid(create(s, "No text", slots(emptyList())), "slots.text" to "text-slot-required")
        val withoutText = """{"name":"No text","slots":{"vision":[],"image":[]}}"""
        assertInvalid(
            call("POST", "/api/v1/models/profiles", s.key, withoutText),
            "slots.text" to "text-slot-required",
        )
        assertThat(names(s)).doesNotContain("No text")
    }

    @Test
    fun `a model that cannot do the slot's job is 400 model-not-capable at its index (AC-216)`() {
        val s = signedIn()
        assertInvalid(
            create(s, "Bad vision", slots(listOf("it/main"), vision = listOf("it/vision", "it/main"))),
            "slots.vision[1]" to "model-not-capable",
        )
        assertInvalid(
            create(s, "Bad image", slots(listOf("it/main"), image = listOf("it/main"))),
            "slots.image[0]" to "model-not-capable",
        )
        assertInvalid(
            create(s, "Bad text", slots(listOf("it/main", "it/image"))),
            "slots.text[1]" to "model-not-capable",
        )
    }

    @Test
    fun `a fourth model is 400 slot-full and a repeated model is 400 model-duplicate (AC-217)`() {
        val s = signedIn()
        assertInvalid(
            create(s, "Four", slots(listOf("it/main", "it/back", "it/own", "it/extra"))),
            "slots.text[3]" to "slot-full",
        )
        assertThat(create(s, "Three", slots(listOf("it/main", "it/back", "it/own"))).statusCode()).isEqualTo(201)
        assertInvalid(
            create(s, "Twice", slots(listOf("it/main", "it/back", "it/main"))),
            "slots.text[2]" to "model-duplicate",
        )
    }

    @Test
    fun `every broken rule is reported at once in errors (AC-214, AC-215, AC-216)`() {
        val s = signedIn()
        assertInvalid(
            create(s, " ", slots(emptyList(), vision = listOf("it/main"))),
            "name" to "name-required",
            "slots.text" to "text-slot-required",
            "slots.vision[0]" to "model-not-capable",
        )
    }

    // ---- AC-218 / AC-226 on writes

    @Test
    fun `creating the 21st custom profile is 409 profile-limit-reached, the 20th is fine (AC-218)`() {
        val s = signedIn()
        repeat(19) { stored(s.owner, "P$it") }
        assertThat(create(s, "Twentieth").statusCode()).isEqualTo(201)
        assertProblem(create(s, "Twenty first"), 409, "profile-limit-reached")
        assertThat(names(s)).doesNotContain("Twenty first")
    }

    @Test
    fun `without a provider key create and update are 409 ai-not-configured (AC-226)`() {
        val s = signedIn()
        val id = stored(s.owner, "Night shift")
        load(CatalogState.NOT_CONFIGURED, emptyList())
        assertProblem(create(s, "X"), 409, "ai-not-configured")
        val put = call("PUT", "/api/v1/models/profiles/${id.value}", s.key, profileJson("Renamed"))
        assertProblem(put, 409, "ai-not-configured")
    }

    // ---- update

    @Test
    fun `a custom profile is replaced in full, in the chosen order (AC-213)`() {
        val s = signedIn()
        val id = stored(s.owner, "Night shift")
        val r =
            call(
                "PUT",
                "/api/v1/models/profiles/${id.value}",
                s.key,
                profileJson("Day shift", slots(listOf("it/main", "it/back"), listOf("it/vision"))),
            )
        assertThat(r.statusCode()).isEqualTo(200)
        val b = body(r)
        assertThat(b["ref"]["id"].asString()).isEqualTo(id.value.toString())
        assertThat(b["name"].asString()).isEqualTo("Day shift")
        assertThat(b["slots"]["text"]["chain"].each().map { it["modelId"].asString() })
            .containsExactly("it/main", "it/back")
        assertThat(b["slots"]["vision"]["currentModelId"].asString()).isEqualTo("it/vision")
        assertThat(names(s)).contains("Day shift").doesNotContain("Night shift")
    }

    @Test
    fun `saving a profile under its own name, in another case, is allowed (AC-214)`() {
        val s = signedIn()
        val id = stored(s.owner, "Night shift")
        val r = call("PUT", "/api/v1/models/profiles/${id.value}", s.key, profileJson("NIGHT SHIFT"))
        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(body(r)["name"].asString()).isEqualTo("NIGHT SHIFT")
    }

    @Test
    fun `an update breaking the rules is 400 with errors and keeps the stored profile (AC-214 to AC-217)`() {
        val s = signedIn()
        stored(s.owner, "Taken")
        val id = stored(s.owner, "Night shift")
        val r =
            call(
                "PUT",
                "/api/v1/models/profiles/${id.value}",
                s.key,
                profileJson("taken", slots(emptyList(), vision = listOf("it/main", "it/main"))),
            )
        assertInvalid(
            r,
            "name" to "name-taken",
            "slots.text" to "text-slot-required",
            "slots.vision[0]" to "model-not-capable",
            "slots.vision[1]" to "model-duplicate", // one code per index; a repeat is reported as a duplicate
        )
        assertThat(names(s)).contains("Night shift")
    }

    @Test
    fun `only a newly added missing model is refused, an older missing one stays marked Not in the catalog (AC-221)`() {
        val s = signedIn()
        val id = stored(s.owner, "Night shift", text = listOf("it/old-gone", "it/own"))
        val path = "/api/v1/models/profiles/${id.value}"
        val refused =
            call("PUT", path, s.key, profileJson("Night shift", slots(listOf("it/old-gone", "it/own", "it/new-gone"))))
        assertInvalid(refused, "slots.text[2]" to "model-left-catalog")
        val kept =
            call("PUT", path, s.key, profileJson("Night shift", slots(listOf("it/old-gone", "it/own", "it/main"))))
        assertThat(kept.statusCode()).isEqualTo(200)
        val chain = body(kept)["slots"]["text"]["chain"].each()
        assertThat(chain.map { it["modelId"].asString() }).containsExactly("it/old-gone", "it/own", "it/main")
        assertThat(chain[0]["availability"].asString()).isEqualTo("not-in-catalog")
    }

    // ---- AC-219

    @Test
    fun `changing or deleting a system profile is 409 system-profile-read-only (AC-219)`() {
        val s = signedIn()
        listOf("balanced", "fast", "careful").forEach {
            assertProblem(
                call("PUT", "/api/v1/models/profiles/$it", s.key, profileJson("Mine")),
                409,
                "system-profile-read-only",
            )
            assertProblem(call("DELETE", "/api/v1/models/profiles/$it", s.key), 409, "system-profile-read-only")
        }
        assertThat(names(s)).containsExactly("Fast and cheap", "Balanced", "Careful")
    }

    // ---- delete, AC-220

    @Test
    fun `deleting the default custom profile returns the default to Balanced and says so (AC-220)`() {
        val s = signedIn()
        val id = stored(s.owner, "Cheap vision")
        assertThat(
            call("PUT", "/api/v1/models/default-profile", s.key, """{"profile":${customRef(id)}}""").statusCode(),
        ).isEqualTo(200)
        val r = call("DELETE", "/api/v1/models/profiles/${id.value}", s.key)
        assertThat(r.statusCode()).isEqualTo(200)
        val b = body(r)
        assertThat(b["defaultReset"].asBoolean()).isTrue()
        assertThat(b["defaultProfile"]["kind"].asString()).isEqualTo("system")
        assertThat(b["defaultProfile"]["key"].asString()).isEqualTo("balanced")
        val after = list(s)
        assertThat(after["items"].each().map { it["name"].asString() }).doesNotContain("Cheap vision")
        assertThat(after["defaultProfile"]["key"].asString()).isEqualTo("balanced")
        assertThat(call("GET", "/api/v1/models/profiles/${id.value}", s.key).statusCode()).isEqualTo(404)
    }

    @Test
    fun `deleting a profile that is not the default leaves the default alone (AC-220)`() {
        val s = signedIn()
        val id = stored(s.owner, "Cheap vision")
        call("PUT", "/api/v1/models/default-profile", s.key, """{"profile":{"kind":"system","key":"careful"}}""")
        val b = body(call("DELETE", "/api/v1/models/profiles/${id.value}", s.key))
        assertThat(b["defaultReset"].asBoolean()).isFalse()
        assertThat(b["defaultProfile"]["key"].asString()).isEqualTo("careful")
        assertThat(list(s)["defaultProfile"]["key"].asString()).isEqualTo("careful")
    }

    // ---- AC-222 cross-Owner

    @Test
    fun `another Owner's profile, an unknown uuid and a malformed key give 404 not-found on PUT and DELETE (AC-222)`() {
        val a = signedIn()
        val b = signedIn()
        val foreign = stored(a.owner, "Night shift")
        listOf(foreign.value.toString(), UUID.randomUUID().toString(), "not-a-key").forEach {
            assertProblem(call("PUT", "/api/v1/models/profiles/$it", b.key, profileJson("Mine")), 404, "not-found")
            assertProblem(call("DELETE", "/api/v1/models/profiles/$it", b.key), 404, "not-found")
        }
        assertThat(names(a)).contains("Night shift")
        assertThat(call("GET", "/api/v1/models/profiles/${foreign.value}", a.key).statusCode()).isEqualTo(200)
    }

    // ---- default profile, AC-51 / AC-223

    @Test
    fun `switching the default to Careful answers 200 with the profile and every option has a price (AC-51)`() {
        val s = signedIn()
        stored(s.owner, "Night shift")
        assertThat(list(s)["defaultProfile"]["key"].asString()).isEqualTo("balanced")
        val r =
            call("PUT", "/api/v1/models/default-profile", s.key, """{"profile":{"kind":"system","key":"careful"}}""")
        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(body(r)["profile"]["kind"].asString()).isEqualTo("system")
        assertThat(body(r)["profile"]["key"].asString()).isEqualTo("careful")
        val after = list(s)
        assertThat(after["defaultProfile"]["key"].asString()).isEqualTo("careful")
        assertThat(after["items"].each().map { it["name"].asString() })
            .containsExactly("Fast and cheap", "Balanced", "Careful", "Night shift")
        after["items"].each().forEach { assertThat(it["pricePer100Runs"]["state"].asString()).isNotEmpty() }
    }

    @Test
    fun `the default can be a custom profile and go back to Balanced (AC-51)`() {
        val s = signedIn()
        val id = stored(s.owner, "Night shift")
        val custom = call("PUT", "/api/v1/models/default-profile", s.key, """{"profile":${customRef(id)}}""")
        assertThat(body(custom)["profile"]["id"].asString()).isEqualTo(id.value.toString())
        assertThat(list(s)["defaultProfile"]["id"].asString()).isEqualTo(id.value.toString())
        assertThat(call("PUT", "/api/v1/models/default-profile", s.key, """{"profile":$balancedRef}""").statusCode())
            .isEqualTo(200)
        assertThat(list(s)["defaultProfile"]["key"].asString()).isEqualTo("balanced")
    }

    @Test
    fun `a body without profile is 400 validation-failed with profile required (AC-51)`() {
        val s = signedIn()
        val r = call("PUT", "/api/v1/models/default-profile", s.key, "{}")
        assertInvalid(r, "profile" to "required")
    }

    @Test
    fun `a profile with no text model in the catalog cannot become the default, 409 no-text-model (AC-223)`() {
        val s = signedIn()
        val gone = stored(s.owner, "Gone", text = listOf("it/old-gone"))
        assertProblem(
            call("PUT", "/api/v1/models/default-profile", s.key, """{"profile":${customRef(gone)}}"""),
            409,
            "no-text-model",
        )
        load(CatalogState.UPDATE_FAILED, emptyList())
        assertProblem(
            call("PUT", "/api/v1/models/default-profile", s.key, """{"profile":{"kind":"system","key":"careful"}}"""),
            409,
            "no-text-model",
        )
        assertThat(list(s)["defaultProfile"]["key"].asString()).isEqualTo("balanced")
    }

    @Test
    fun `another Owner's or an unknown profile as the default is 404 not-found (AC-222)`() {
        val a = signedIn()
        val b = signedIn()
        val foreign = stored(a.owner, "Night shift")
        // The route exists for the caller's own choice, so the 404 below is about the profile, not the path.
        assertThat(
            call("PUT", "/api/v1/models/default-profile", a.key, """{"profile":${customRef(foreign)}}""").statusCode(),
        ).isEqualTo(200)
        assertProblem(
            call("PUT", "/api/v1/models/default-profile", b.key, """{"profile":${customRef(foreign)}}"""),
            404,
            "not-found",
        )
        val unknown = customRef(ModelProfileId(UUID.randomUUID()))
        assertProblem(
            call("PUT", "/api/v1/models/default-profile", b.key, """{"profile":$unknown}"""),
            404,
            "not-found",
        )
        assertThat(list(b)["defaultProfile"]["key"].asString()).isEqualTo("balanced")
    }
}
