package telex.agents.profile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.web.ErrorResponseException
import telex.TestcontainersConfiguration
import telex.agents.AiNotConfigured
import telex.agents.AvailabilityView
import telex.agents.ModelProfileDeleted
import telex.agents.ModelProfileId
import telex.agents.ModelProfileView
import telex.agents.ModelProfiles
import telex.agents.ModelSlotKind
import telex.agents.NoTextModel
import telex.agents.PriceViewState
import telex.agents.ProfileInvalid
import telex.agents.ProfileLimitReached
import telex.agents.ProfileNotFound
import telex.agents.ProfileRef
import telex.agents.SlotViewState
import telex.agents.SystemProfileKey
import telex.agents.SystemProfileReadOnly
import telex.agents.internal.profile.DefaultProfileRepository
import telex.agents.internal.profile.ModelProfile
import telex.agents.internal.profile.ProfileRepository
import telex.identity.OwnerId
import telex.llm.CatalogModel
import telex.llm.CatalogSnapshot
import telex.llm.CatalogState
import telex.llm.Modality
import telex.llm.ModelCatalog
import telex.llm.ModelId
import telex.shared.FieldProblem
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

/** Write side of the Models page (T12): AC-51, 213, 218, 219, 220, 221, 223, 226 and Owner scoping (AC-222). */
@SpringBootTest(
    properties = [
        "telex.models.system-profiles.balanced.text=it/main,it/missing,it/back",
        "telex.models.system-profiles.balanced.vision=it/vision",
        "telex.models.system-profiles.balanced.image=it/image",
        "telex.models.system-profiles.careful.text=it/back",
    ],
)
@RecordApplicationEvents
@Import(TestcontainersConfiguration::class, ModelProfilesWriteIT.MutableCatalog::class)
class ModelProfilesWriteIT {
    class SwitchableCatalog : ModelCatalog {
        @Volatile var current: CatalogSnapshot = CatalogSnapshot(emptyMap(), null, null, CatalogState.NOT_LOADED)

        override fun snapshot() = current

        override fun find(modelId: ModelId) = current.models[modelId]
    }

    @TestConfiguration
    class MutableCatalog {
        @Bean
        @Primary
        fun writeSwitchableCatalog(): ModelCatalog = SwitchableCatalog()
    }

    @Autowired lateinit var service: ModelProfiles

    @Autowired lateinit var catalog: ModelCatalog

    @Autowired lateinit var profiles: ProfileRepository

    @Autowired lateinit var defaults: DefaultProfileRepository

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var events: ApplicationEvents

    private val text = setOf(Modality.TEXT)
    private val balanced = ProfileRef.System(SystemProfileKey.BALANCED)
    private val careful = ProfileRef.System(SystemProfileKey.CAREFUL)

    private fun model(
        id: String,
        takes: Set<Modality>,
        produces: Set<Modality>,
        input: String? = null,
        output: String? = null,
    ) = CatalogModel(
        ModelId(id),
        id,
        "prov",
        takes,
        produces,
        input?.let(::BigDecimal),
        output?.let(::BigDecimal),
        null,
        128_000,
    )

    private val fixtures =
        listOf(
            model("it/main", text, text, "1.00", "2.00"),
            model("it/back", text, text, "10.00", "20.00"),
            model("it/vision", setOf(Modality.TEXT, Modality.IMAGE), text, "0.50", "1.00"),
            model("it/image", text, setOf(Modality.IMAGE)),
            model("it/own", text, text, "1.00", "2.00"),
            model("it/own2", text, text, "1.00", "2.00"),
            model("it/own3", text, text, "1.00", "2.00"),
            model("it/none", setOf(Modality.IMAGE), emptySet()),
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

    private fun owner(): OwnerId {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        return OwnerId(id)
    }

    private fun slots(
        text: List<String> = listOf("it/own"),
        vision: List<String> = emptyList(),
        image: List<String> = emptyList(),
    ) = mapOf(
        ModelSlotKind.TEXT to text,
        ModelSlotKind.VISION to vision,
        ModelSlotKind.IMAGE to image,
    )

    private fun stored(
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
                slots(text).mapValues { (_, ids) ->
                    ids.map(::ModelId)
                },
            ),
            Instant.now(),
        )
        return id
    }

    private fun customId(v: ModelProfileView) = (v.ref as ProfileRef.Custom).id

    private fun thrown(block: () -> Any?): ErrorResponseException =
        runCatching(block).exceptionOrNull() as? ErrorResponseException ?: error("expected a problem")

    private fun code(e: ErrorResponseException) = e.body.properties?.get("code")

    @Suppress("UNCHECKED_CAST")
    private fun errors(e: ErrorResponseException) =
        (e.body.properties?.get("errors") as List<FieldProblem>).map { it.field to it.code }

    private fun invalid(block: () -> Any?): List<Pair<String, String>> {
        val e = thrown(block)
        assertThat(e).isInstanceOf(ProfileInvalid::class.java)
        assertThat(code(e)).isEqualTo("validation-failed")
        assertThat(e.statusCode.value()).isEqualTo(400)
        return errors(e)
    }

    private fun defaultRows(o: OwnerId) =
        jdbc.queryForObject("SELECT count(*) FROM default_model_profile WHERE owner_id = ?", Int::class.java, o.value)

    // ---- create (AC-213)

    @Test
    fun `duplicating Balanced keeps source models that left the catalog (AC-213)`() {
        val o = owner()
        val view =
            service.create(o, " Cheap vision ", slots(listOf("it/back", "it/main"), listOf("it/vision")), balanced)
        assertThat(view.ref).isInstanceOf(ProfileRef.Custom::class.java)
        assertThat(view.name).isEqualTo("Cheap vision")
        val t = view.slots.getValue(ModelSlotKind.TEXT)
        assertThat(t.chain.map { it.modelId }).containsExactly("it/back", "it/main")
        assertThat(t.currentModelId).isEqualTo("it/back")
        assertThat(view.slots.getValue(ModelSlotKind.VISION).state).isEqualTo(SlotViewState.MAIN_MODEL)
        assertThat(view.slots.getValue(ModelSlotKind.IMAGE).state).isEqualTo(SlotViewState.NOT_USED)
        assertThat(view.price.amount).isEqualByComparingTo("4.00")
        assertThat(service.list(o).items.map { it.name }).contains("Cheap vision")
        assertThat(
            profiles
                .find(o, customId(view))!!
                .slots
                .getValue(ModelSlotKind.TEXT)
                .map { it.value },
        ).containsExactly("it/back", "it/main")
        assertThat(service.list(o).defaultProfile).isEqualTo(balanced)

        val copy = service.create(o, "Copy", slots(listOf("it/main", "it/missing", "it/back")), balanced)
        assertThat(
            copy.slots
                .getValue(ModelSlotKind.TEXT)
                .chain
                .map { it.availability },
        ).containsExactly(AvailabilityView.AVAILABLE, AvailabilityView.NOT_IN_CATALOG, AvailabilityView.AVAILABLE)
    }

    @Test
    fun `a model missing from the catalog is refused when it did not come from the duplicated source (AC-221)`() {
        val o = owner()
        val errs = invalid { service.create(o, "Fresh", slots(listOf("it/main", "it/missing"))) }
        assertThat(errs).containsExactly("slots.text[1]" to "model-left-catalog")
        assertThat(profiles.count(o)).isZero()
    }

    @Test
    fun `every rule failure is reported with its field and code and nothing is stored`() {
        val o = owner()
        stored(o, "Taken")
        assertThat(invalid { service.create(o, "   ", slots()) }).containsExactly("name" to "name-required")
        assertThat(invalid { service.create(o, "x".repeat(41), slots()) }).containsExactly("name" to "name-too-long")
        assertThat(invalid { service.create(o, "balanced", slots()) }).containsExactly("name" to "name-reserved")
        assertThat(invalid { service.create(o, "FAST AND CHEAP", slots()) }).containsExactly("name" to "name-reserved")
        assertThat(invalid { service.create(o, " taken ", slots()) }).containsExactly("name" to "name-taken")
        assertThat(invalid { service.create(o, "A", slots(text = emptyList())) })
            .containsExactly("slots.text" to "text-slot-required")
        assertThat(invalid { service.create(o, "A", slots(text = listOf("it/none"))) })
            .containsExactly("slots.text[0]" to "model-not-capable")
        assertThat(invalid { service.create(o, "A", slots(listOf("it/own", "it/own2", "it/own3", "it/main"))) })
            .containsExactly("slots.text[3]" to "slot-full")
        assertThat(invalid { service.create(o, "A", slots(listOf("it/own", "it/own"))) })
            .containsExactly("slots.text[1]" to "model-duplicate")
        assertThat(invalid { service.create(o, "", slots(text = emptyList())) })
            .containsExactlyInAnyOrder("name" to "name-required", "slots.text" to "text-slot-required")
        assertThat(profiles.count(o)).isEqualTo(1)
    }

    @Test
    fun `the same name is free for another Owner`() {
        stored(owner(), "Shared")
        assertThat(service.create(owner(), "Shared", slots()).name).isEqualTo("Shared")
    }

    // ---- limit (AC-218)

    @Test
    fun `the 21st profile is refused whether created or duplicated and nothing is created (AC-218)`() {
        val o = owner()
        repeat(20) { stored(o, "P$it") }
        val e = thrown { service.create(o, "One more", slots()) }
        assertThat(e).isInstanceOf(ProfileLimitReached::class.java)
        assertThat(code(e)).isEqualTo("profile-limit-reached")
        assertThat(thrown { service.create(o, "Dup", slots(), balanced) }).isInstanceOf(ProfileLimitReached::class.java)
        assertThat(profiles.count(o)).isEqualTo(20)
    }

    @Test
    fun `two parallel creates for the 20th place let exactly one win (AC-218)`() {
        val o = owner()
        repeat(19) { stored(o, "P$it") }
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results =
                (1..2)
                    .map { n ->
                        pool.submit<Throwable?> {
                            barrier.await()
                            runCatching { service.create(o, "Racer $n", slots()) }.exceptionOrNull()
                        }
                    }.map { it.get() }
            assertThat(results.count { it == null }).isEqualTo(1)
            assertThat(results.filterNotNull().single()).isInstanceOf(ProfileLimitReached::class.java)
            assertThat(profiles.count(o)).isEqualTo(20)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `two parallel creates with the same name let one win and the other gets name-taken`() {
        val o = owner()
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results =
                (1..2)
                    .map {
                        pool.submit<Throwable?> {
                            barrier.await()
                            runCatching { service.create(o, "Same", slots()) }.exceptionOrNull()
                        }
                    }.map { it.get() }
            assertThat(results.count { it == null }).isEqualTo(1)
            val e = results.filterNotNull().single() as ErrorResponseException
            assertThat(e).isInstanceOf(ProfileInvalid::class.java)
            assertThat(errors(e)).containsExactly("name" to "name-taken")
            assertThat(profiles.count(o)).isEqualTo(1)
        } finally {
            pool.shutdownNow()
        }
    }

    // ---- update (AC-221)

    @Test
    fun `update renames and replaces the chains in the chosen order and keeps the id`() {
        val o = owner()
        val id = stored(o, "Old", listOf("it/own", "it/own2"))
        val view =
            service.update(o, ProfileRef.Custom(id), "Old", slots(listOf("it/own2", "it/own"), listOf("it/vision")))
        assertThat(view.ref).isEqualTo(ProfileRef.Custom(id))
        assertThat(view.name).isEqualTo("Old") // its own name is not "taken"
        val saved = profiles.find(o, id)!!
        assertThat(saved.slots.getValue(ModelSlotKind.TEXT).map { it.value }).containsExactly("it/own2", "it/own")
        assertThat(saved.slots.getValue(ModelSlotKind.VISION).map { it.value }).containsExactly("it/vision")
        assertThat(service.update(o, ProfileRef.Custom(id), "New", slots()).name).isEqualTo("New")
        assertThat(profiles.count(o)).isEqualTo(1)
    }

    @Test
    fun `a newly added model that left the catalog refuses the save, an older missing one stays (AC-221)`() {
        val o = owner()
        val id = stored(o, "Mine", listOf("it/main", "it/old-gone"))
        val ref = ProfileRef.Custom(id)
        val errs = invalid { service.update(o, ref, "Mine", slots(listOf("it/main", "it/old-gone", "it/new-gone"))) }
        assertThat(errs).containsExactly("slots.text[2]" to "model-left-catalog")
        assertThat(
            profiles
                .find(o, id)!!
                .slots
                .getValue(ModelSlotKind.TEXT)
                .map { it.value },
        ).containsExactly("it/main", "it/old-gone")

        val view = service.update(o, ref, "Mine", slots(listOf("it/old-gone", "it/main", "it/own")))
        assertThat(
            view.slots
                .getValue(ModelSlotKind.TEXT)
                .chain
                .map { it.availability },
        ).containsExactly(AvailabilityView.NOT_IN_CATALOG, AvailabilityView.AVAILABLE, AvailabilityView.AVAILABLE)
    }

    @Test
    fun `a rename onto another own profile's name fails and leaves the profile unchanged`() {
        val o = owner()
        stored(o, "Other")
        val id = stored(o, "Mine", listOf("it/own"))
        assertThat(invalid { service.update(o, ProfileRef.Custom(id), "other", slots(listOf("it/main"))) })
            .containsExactly("name" to "name-taken")
        assertThat(profiles.find(o, id)!!.name).isEqualTo("Mine")
        assertThat(
            profiles
                .find(o, id)!!
                .slots
                .getValue(ModelSlotKind.TEXT)
                .map { it.value },
        ).containsExactly("it/own")
    }

    // ---- system profiles read only (AC-219)

    @Test
    fun `system profiles can be neither changed nor deleted (AC-219)`() {
        val o = owner()
        val upd = thrown { service.update(o, balanced, "Mine", slots()) }
        assertThat(upd).isInstanceOf(SystemProfileReadOnly::class.java)
        assertThat(code(upd)).isEqualTo("system-profile-read-only")
        assertThat(upd.statusCode.value()).isEqualTo(409)
        val del = thrown { service.delete(o, balanced) }
        assertThat(del).isInstanceOf(SystemProfileReadOnly::class.java)
        assertThat(events.stream(ModelProfileDeleted::class.java).count()).isZero()
    }

    // ---- delete (AC-220)

    @Test
    fun `deleting the default custom profile resets the default to Balanced and publishes the event (AC-220)`() {
        val o = owner()
        val id = stored(o, "Cheap vision")
        defaults.set(o, ProfileRef.Custom(id), Instant.now())
        val result = service.delete(o, ProfileRef.Custom(id))
        assertThat(result.defaultReset).isTrue()
        assertThat(result.defaultProfile).isEqualTo(balanced)
        assertThat(profiles.find(o, id)).isNull()
        assertThat(service.list(o).items.map { it.name }).doesNotContain("Cheap vision")
        assertThat(service.list(o).defaultProfile).isEqualTo(balanced)
        assertThat(defaultRows(o)).isZero()
        val chainRows =
            jdbc.queryForObject(
                "SELECT count(*) FROM model_profile_slot_model WHERE model_profile_id = ?",
                Int::class.java,
                id.value,
            )
        assertThat(chainRows).isZero()
        assertThat(events.stream(ModelProfileDeleted::class.java).toList())
            .contains(ModelProfileDeleted(o, id, true))
    }

    @Test
    fun `deleting a non-default profile keeps the default and says wasDefault false`() {
        val o = owner()
        val id = stored(o, "Spare")
        service.setDefault(o, careful)
        val result = service.delete(o, ProfileRef.Custom(id))
        assertThat(result.defaultReset).isFalse()
        assertThat(result.defaultProfile).isEqualTo(careful)
        assertThat(events.stream(ModelProfileDeleted::class.java).toList())
            .contains(ModelProfileDeleted(o, id, false))
    }

    @Test
    fun `delete still works when no provider key is configured (AC-226)`() {
        val o = owner()
        val id = stored(o, "Spare")
        load(CatalogState.NOT_CONFIGURED, emptyList())
        assertThat(service.delete(o, ProfileRef.Custom(id)).defaultReset).isFalse()
        assertThat(profiles.find(o, id)).isNull()
    }

    // ---- choose the default (AC-51, AC-223)

    @Test
    fun `switching the default to Careful stores it and choosing Balanced removes the row (AC-51)`() {
        val o = owner()
        val mine = stored(o, "Mine")
        assertThat(service.setDefault(o, careful)).isEqualTo(careful)
        assertThat(service.list(o).defaultProfile).isEqualTo(careful)
        assertThat(service.setDefault(o, ProfileRef.Custom(mine))).isEqualTo(ProfileRef.Custom(mine))
        assertThat(service.list(o).defaultProfile).isEqualTo(ProfileRef.Custom(mine))
        assertThat(service.setDefault(o, balanced)).isEqualTo(balanced)
        assertThat(defaultRows(o)).isZero()
        assertThat(service.list(o).defaultProfile).isEqualTo(balanced)
    }

    @Test
    fun `a profile with no text model can't become the default and the current default stays (AC-223)`() {
        val o = owner()
        val orphan = stored(o, "Orphan", listOf("it/gone"))
        service.setDefault(o, careful)
        val e = thrown { service.setDefault(o, ProfileRef.Custom(orphan)) }
        assertThat(e).isInstanceOf(NoTextModel::class.java)
        assertThat(code(e)).isEqualTo("no-text-model")
        assertThat(e.statusCode.value()).isEqualTo(409)
        assertThat(service.list(o).defaultProfile).isEqualTo(careful)
        assertThat(service.get(o, ProfileRef.Custom(orphan)).price.state).isEqualTo(PriceViewState.NO_TEXT_MODEL)
        // Fast and cheap has no model in this catalog either
        assertThat(thrown { service.setDefault(o, ProfileRef.System(SystemProfileKey.FAST)) })
            .isInstanceOf(NoTextModel::class.java)
    }

    // ---- not configured (AC-226)

    @Test
    fun `without a provider key create and update are refused with ai-not-configured (AC-226)`() {
        val o = owner()
        val id = stored(o, "Mine")
        load(CatalogState.NOT_CONFIGURED, emptyList())
        val c = thrown { service.create(o, "New", slots()) }
        assertThat(c).isInstanceOf(AiNotConfigured::class.java)
        assertThat(code(c)).isEqualTo("ai-not-configured")
        assertThat(thrown { service.update(o, ProfileRef.Custom(id), "Renamed", slots()) })
            .isInstanceOf(AiNotConfigured::class.java)
        assertThat(profiles.find(o, id)!!.name).isEqualTo("Mine")
        assertThat(profiles.count(o)).isEqualTo(1)
    }

    // ---- Owner scoping (AC-222)

    @Test
    fun `another Owner's profile is not found for every write and stays untouched (AC-222)`() {
        val first = owner()
        val second = owner()
        val id = stored(first, "Night shift")
        defaults.set(first, ProfileRef.Custom(id), Instant.now())
        val foreign = ProfileRef.Custom(id)
        val missing = ProfileRef.Custom(ModelProfileId(UUID.randomUUID()))

        listOf(
            { service.update(second, foreign, "Hijack", slots()) },
            { service.delete(second, foreign) },
            { service.setDefault(second, foreign) },
            { service.create(second, "Dup", slots(), foreign) },
        ).forEach {
            val e = thrown(it)
            assertThat(e).isInstanceOf(ProfileNotFound::class.java)
            assertThat(code(e)).isEqualTo("not-found")
        }
        assertThat(thrown { service.create(second, "Dup", slots(), missing) }).isInstanceOf(ProfileNotFound::class.java)
        assertThat(thrown { service.update(second, missing, "X", slots()) }).isInstanceOf(ProfileNotFound::class.java)
        assertThat(thrown { service.delete(second, missing) }).isInstanceOf(ProfileNotFound::class.java)

        assertThat(profiles.find(first, id)!!.name).isEqualTo("Night shift")
        assertThat(service.list(first).defaultProfile).isEqualTo(foreign)
        assertThat(profiles.count(second)).isZero()
        assertThat(events.stream(ModelProfileDeleted::class.java).toList().filter { it.ownerId == second }).isEmpty()
    }
}
