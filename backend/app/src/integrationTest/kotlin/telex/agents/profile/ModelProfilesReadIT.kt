package telex.agents.profile

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.ErrorResponseException
import telex.TestcontainersConfiguration
import telex.agents.AiNotConfigured
import telex.agents.AvailabilityView
import telex.agents.CatalogViewState
import telex.agents.ModelProfileId
import telex.agents.ModelProfiles
import telex.agents.ModelSlotKind
import telex.agents.PriceViewState
import telex.agents.ProfileLimitReached
import telex.agents.ProfileNotFound
import telex.agents.ProfileRef
import telex.agents.SlotViewState
import telex.agents.SystemProfileKey
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Read side of the Models page (T11): AC-211, 212, 213 (draft), 218, 222, 225, 226. */
@SpringBootTest(
    properties = [
        "telex.models.system-profiles.balanced.text=it/main,it/missing,it/back",
        "telex.models.system-profiles.balanced.vision=it/vision",
        "telex.models.system-profiles.balanced.image=it/image",
        "telex.models.system-profiles.fast.text=it/main",
    ],
)
@Import(TestcontainersConfiguration::class, ModelProfilesReadIT.MutableCatalog::class)
class ModelProfilesReadIT {
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

    @Autowired lateinit var service: ModelProfiles

    @Autowired lateinit var catalog: ModelCatalog

    @Autowired lateinit var profiles: ProfileRepository

    @Autowired lateinit var defaults: DefaultProfileRepository

    @Autowired lateinit var jdbc: JdbcTemplate

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

    private fun owner(): OwnerId {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        return OwnerId(id)
    }

    private fun custom(
        o: OwnerId,
        name: String,
        text: List<String> = listOf("it/own"),
        vision: List<String> = emptyList(),
        at: Instant = Instant.now(),
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
            at,
        )
        return id
    }

    private fun problemCode(t: Throwable) = (t as ErrorResponseException).body.properties?.get("code")

    // ---- AC-211 / AC-212 catalog

    @Test
    fun `catalog lists models by name with slots, prices, context and last-updated time (AC-211)`() {
        val view = service.catalog()
        assertThat(view.state).isEqualTo(CatalogViewState.CURRENT)
        assertThat(view.lastRefreshedAt).isEqualTo(refreshed)
        assertThat(view.lastFailedAt).isNull()
        assertThat(view.models.map { it.name })
            .containsExactly("Alpha back", "Draw", "Mid vision", "Own text", "Zeta main")
        val vision = view.models.first { it.modelId == "it/vision" }
        assertThat(vision.provider).isEqualTo("prov")
        assertThat(vision.slots).containsExactlyInAnyOrder(ModelSlotKind.TEXT, ModelSlotKind.VISION)
        assertThat(vision.takes).hasSize(2)
        assertThat(vision.inputPerMtok).isEqualByComparingTo("0.50")
        assertThat(vision.outputPerMtok).isEqualByComparingTo("1.00")
        assertThat(vision.contextLength).isEqualTo(128_000)
        val image = view.models.first { it.modelId == "it/image" }
        assertThat(image.slots).containsExactly(ModelSlotKind.IMAGE)
        assertThat(image.perImage).isEqualByComparingTo("0.04")
        assertThat(image.contextLength).isNull()
    }

    @Test
    fun `a failed refresh serves the last known list with both times (AC-212)`() {
        load(CatalogState.UPDATE_FAILED, failedAt = failed)
        val view = service.catalog()
        assertThat(view.state).isEqualTo(CatalogViewState.UPDATE_FAILED)
        assertThat(view.lastRefreshedAt).isEqualTo(refreshed)
        assertThat(view.lastFailedAt).isEqualTo(failed)
        assertThat(view.models).hasSize(5)
    }

    @Test
    fun `never loaded catalog is empty and every system slot shows no model available (AC-212)`() {
        load(CatalogState.NOT_LOADED, emptyList(), refreshedAt = null)
        val view = service.catalog()
        assertThat(view.state).isEqualTo(CatalogViewState.NOT_LOADED)
        assertThat(view.lastRefreshedAt).isNull()
        assertThat(view.models).isEmpty()
        val list = service.list(owner())
        assertThat(list.aiConfigured).isTrue()
        list.items.take(3).forEach { p ->
            ModelSlotKind.entries.forEach {
                assertThat(p.slots.getValue(it).state)
                    .describedAs("${p.name} $it")
                    .isEqualTo(SlotViewState.NO_MODEL_AVAILABLE)
            }
            assertThat(p.choosable).isFalse()
            assertThat(p.price.state).isEqualTo(PriceViewState.NO_TEXT_MODEL)
        }
    }

    // ---- AC-226 not configured

    @Test
    fun `without a provider key the list says so, system slots have no model and the draft is refused (AC-226)`() {
        load(CatalogState.NOT_CONFIGURED, emptyList(), refreshedAt = null)
        assertThat(service.catalog().state).isEqualTo(CatalogViewState.NOT_CONFIGURED)
        val o = owner()
        custom(o, "Mine")
        val list = service.list(o)
        assertThat(list.aiConfigured).isFalse()
        assertThat(list.items.map { it.name }).containsExactly("Fast and cheap", "Balanced", "Careful", "Mine")
        list.items.take(3).forEach {
            assertThat(it.slots.getValue(ModelSlotKind.TEXT).state).isEqualTo(SlotViewState.NO_MODEL_AVAILABLE)
        }
        assertThatThrownBy { service.draft(o, null) }.isInstanceOf(AiNotConfigured::class.java)
        assertThatThrownBy { service.draft(o, ProfileRef.System(SystemProfileKey.BALANCED)) }
            .isInstanceOf(AiNotConfigured::class.java)
        assertThat(problemCode(AiNotConfigured())).isEqualTo("ai-not-configured")
    }

    // ---- AC-225 system profiles, slot states, price

    @Test
    fun `system profiles come in order with the Operator's exact models for Balanced (AC-225)`() {
        val list = service.list(owner())
        assertThat(list.customProfileLimit).isEqualTo(20)
        assertThat(list.items.map { it.ref })
            .containsExactly(
                ProfileRef.System(SystemProfileKey.FAST),
                ProfileRef.System(SystemProfileKey.BALANCED),
                ProfileRef.System(SystemProfileKey.CAREFUL),
            )
        val balanced = list.items[1]
        assertThat(balanced.name).isEqualTo("Balanced")
        val textSlot = balanced.slots.getValue(ModelSlotKind.TEXT)
        assertThat(textSlot.chain.map { it.modelId }).containsExactly("it/main", "it/missing", "it/back")
        assertThat(textSlot.chain.map { it.availability })
            .containsExactly(AvailabilityView.AVAILABLE, AvailabilityView.NOT_IN_CATALOG, AvailabilityView.AVAILABLE)
        assertThat(textSlot.chain[0].name).isEqualTo("Zeta main")
        assertThat(textSlot.chain[1].name).isNull()
        assertThat(textSlot.state).isEqualTo(SlotViewState.MAIN_MODEL)
        assertThat(textSlot.currentModelId).isEqualTo("it/main")
        assertThat(
            balanced.slots
                .getValue(ModelSlotKind.VISION)
                .chain
                .map { it.modelId },
        ).containsExactly("it/vision")
        // shipped default of Careful: none of its models is in the fixture catalog
        val careful = list.items[2]
        assertThat(careful.slots.getValue(ModelSlotKind.TEXT).chain).isNotEmpty()
        assertThat(careful.slots.getValue(ModelSlotKind.TEXT).state).isEqualTo(SlotViewState.NO_MODEL_AVAILABLE)
        assertThat(careful.choosable).isFalse()
    }

    @Test
    fun `price per 100 runs comes from the text slot's current model (3000 in, 500 out)`() {
        val balanced = service.get(owner(), ProfileRef.System(SystemProfileKey.BALANCED))
        assertThat(balanced.price.state).isEqualTo(PriceViewState.ESTIMATE)
        assertThat(balanced.price.amount).isEqualByComparingTo("0.40")
        assertThat(balanced.choosable).isTrue()
    }

    @Test
    fun `the first available model is current and the slot reads fallback when the main one left the catalog`() {
        val o = owner()
        val id = custom(o, "Backup", text = listOf("it/gone", "it/back"), vision = emptyList())
        val p = service.get(o, ProfileRef.Custom(id))
        val slot = p.slots.getValue(ModelSlotKind.TEXT)
        assertThat(slot.state).isEqualTo(SlotViewState.FALLBACK)
        assertThat(slot.currentModelId).isEqualTo("it/back")
        assertThat(p.slots.getValue(ModelSlotKind.VISION).state).isEqualTo(SlotViewState.NOT_USED)
        assertThat(p.slots.getValue(ModelSlotKind.IMAGE).state).isEqualTo(SlotViewState.NOT_USED)
        assertThat(p.price.amount).isEqualByComparingTo("4.00") // 100*(3000*10+500*20)/1e6
    }

    // ---- list, default, ownership (AC-222)

    @Test
    fun `default is Balanced until chosen, then the chosen one, and customs follow by creation time`() {
        val o = owner()
        val later = custom(o, "Later", at = Instant.parse("2026-10-03T10:00:00Z"))
        val earlier = custom(o, "Earlier", at = Instant.parse("2026-10-03T09:00:00Z"))
        var list = service.list(o)
        assertThat(list.defaultProfile).isEqualTo(ProfileRef.System(SystemProfileKey.BALANCED))
        assertThat(list.items.drop(3).map { it.ref })
            .containsExactly(ProfileRef.Custom(earlier), ProfileRef.Custom(later))
        defaults.set(o, ProfileRef.Custom(later), Instant.now())
        list = service.list(o)
        assertThat(list.defaultProfile).isEqualTo(ProfileRef.Custom(later))
    }

    @Test
    fun `a default whose text model is gone stays the default but is not choosable`() {
        val o = owner()
        val id = custom(o, "Orphan", text = listOf("it/gone"))
        defaults.set(o, ProfileRef.Custom(id), Instant.now())
        val list = service.list(o)
        assertThat(list.defaultProfile).isEqualTo(ProfileRef.Custom(id))
        val item = list.items.first { it.ref == ProfileRef.Custom(id) }
        assertThat(item.choosable).isFalse()
        assertThat(item.price.state).isEqualTo(PriceViewState.NO_TEXT_MODEL)
    }

    @Test
    fun `another Owner never sees a custom profile and gets the same not-found as for a random id (AC-222)`() {
        val first = owner()
        val second = owner()
        val night = custom(first, "Night shift")
        defaults.set(first, ProfileRef.Custom(night), Instant.now())

        assertThat(service.list(second).items.map { it.name }).doesNotContain("Night shift")
        assertThat(service.list(second).defaultProfile).isEqualTo(ProfileRef.System(SystemProfileKey.BALANCED))
        assertThat(service.get(first, ProfileRef.Custom(night)).name).isEqualTo("Night shift")

        val foreign = catchThrowable { service.get(second, ProfileRef.Custom(night)) }
        val missing = catchThrowable { service.get(second, ProfileRef.Custom(ModelProfileId(UUID.randomUUID()))) }
        assertThat(foreign).isInstanceOf(ProfileNotFound::class.java)
        assertThat(missing).isInstanceOf(ProfileNotFound::class.java)
        assertThat(problemCode(foreign)).isEqualTo("not-found")
        assertThat((foreign as ErrorResponseException).body.detail)
            .isEqualTo((missing as ErrorResponseException).body.detail)
        assertThat(foreign.statusCode).isEqualTo(missing.statusCode)
    }

    private fun catchThrowable(block: () -> Unit): Throwable = runCatching(block).exceptionOrNull() ?: error("no throw")

    // ---- drafts (AC-213, AC-218)

    @Test
    fun `a draft without a source is empty, unnamed and has no price`() {
        val d = service.draft(owner(), null)
        assertThat(d.name).isEmpty()
        assertThat(d.duplicatedFrom).isNull()
        ModelSlotKind.entries.forEach { assertThat(d.slots.getValue(it).chain).isEmpty() }
        assertThat(d.slots.getValue(ModelSlotKind.TEXT).state).isEqualTo(SlotViewState.NO_MODEL_AVAILABLE)
        assertThat(d.slots.getValue(ModelSlotKind.VISION).state).isEqualTo(SlotViewState.NOT_USED)
        assertThat(d.price.state).isEqualTo(PriceViewState.NO_TEXT_MODEL)
    }

    @Test
    fun `duplicating Balanced copies every model including ones that left the catalog (AC-213)`() {
        val balanced = ProfileRef.System(SystemProfileKey.BALANCED)
        val d = service.draft(owner(), balanced)
        assertThat(d.name).isEqualTo("Balanced copy")
        assertThat(d.duplicatedFrom).isEqualTo(balanced)
        val text = d.slots.getValue(ModelSlotKind.TEXT)
        assertThat(text.chain.map { it.modelId }).containsExactly("it/main", "it/missing", "it/back")
        assertThat(text.chain[1].availability).isEqualTo(AvailabilityView.NOT_IN_CATALOG)
        assertThat(d.price.amount).isEqualByComparingTo("0.40")
    }

    @Test
    fun `the copy name moves to copy 2 when taken and a copy of an own profile is named after it`() {
        val o = owner()
        val balanced = ProfileRef.System(SystemProfileKey.BALANCED)
        custom(o, "Balanced copy")
        assertThat(service.draft(o, balanced).name).isEqualTo("Balanced copy 2")
        val mine = custom(o, "Cheap")
        val d = service.draft(o, ProfileRef.Custom(mine))
        assertThat(d.name).isEqualTo("Cheap copy")
        assertThat(d.duplicatedFrom).isEqualTo(ProfileRef.Custom(mine))
    }

    @Test
    fun `a draft from another Owner's profile is not found (AC-222)`() {
        val first = owner()
        val id = custom(first, "Night shift")
        assertThatThrownBy { service.draft(owner(), ProfileRef.Custom(id)) }.isInstanceOf(ProfileNotFound::class.java)
    }

    @Test
    fun `with 20 custom profiles no draft opens, with 19 it does (AC-218)`() {
        val o = owner()
        repeat(19) { custom(o, "P$it") }
        assertThat(service.draft(o, null).name).isEmpty()
        custom(o, "P19")
        assertThatThrownBy { service.draft(o, null) }.isInstanceOf(ProfileLimitReached::class.java)
        assertThatThrownBy { service.draft(o, ProfileRef.System(SystemProfileKey.BALANCED)) }
            .isInstanceOf(ProfileLimitReached::class.java)
        assertThat(problemCode(ProfileLimitReached())).isEqualTo("profile-limit-reached")
        assertThat(service.list(o).items).hasSize(23)
    }
}
