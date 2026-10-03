package telex.agents.internal.profile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import telex.agents.ModelProfileId
import telex.agents.ModelSlotKind
import telex.agents.ProfileRef
import telex.agents.SystemProfileKey
import telex.llm.CatalogModel
import telex.llm.Modality
import telex.llm.ModelId
import telex.shared.Uuid7

class ProfileRulesTest {
    private fun cm(
        id: String,
        takes: Set<Modality>,
        produces: Set<Modality>,
    ) = CatalogModel(ModelId(id), id, "p", takes, produces, null, null, null, null)

    private val text = setOf(Modality.TEXT)
    private val both = setOf(Modality.TEXT, Modality.IMAGE)
    private val catalog: Map<ModelId, CatalogModel> =
        listOf(
            cm("t1", text, text),
            cm("t2", text, text),
            cm("t3", text, text),
            cm("t4", text, text),
            cm("v1", both, text),
            cm("i1", text, setOf(Modality.IMAGE)),
        ).associateBy { it.modelId }

    private fun ids(vararg s: String) = s.map { ModelId(it) }

    private fun draft(
        name: String = "Cheap vision",
        text: List<ModelId> = ids("t1"),
        vision: List<ModelId> = emptyList(),
        image: List<ModelId> = emptyList(),
    ) = ProfileDraft(
        name,
        mapOf(ModelSlotKind.TEXT to text, ModelSlotKind.VISION to vision, ModelSlotKind.IMAGE to image),
    )

    private fun validate(
        d: ProfileDraft,
        own: Collection<String> = emptyList(),
        baseline: Map<ModelSlotKind, List<ModelId>> = emptyMap(),
        cat: Map<ModelId, CatalogModel> = catalog,
    ) = ProfileRules.validate(d, cat, own, baseline)

    @Test
    fun `valid profile with empty image slot has no errors AC-213`() {
        val d = draft(text = ids("t1", "t2"), vision = ids("v1"))
        assertThat(validate(d)).isEmpty()
    }

    @Test
    fun `copy name appends copy then numbers AC-213`() {
        assertThat(ProfileRules.copyName("Balanced", emptyList())).isEqualTo("Balanced copy")
        assertThat(ProfileRules.copyName("Balanced", listOf("Balanced copy"))).isEqualTo("Balanced copy 2")
        assertThat(ProfileRules.copyName("Balanced", listOf("Balanced copy", "Balanced copy 2")))
            .isEqualTo("Balanced copy 3")
    }

    @Test
    fun `copy name taken check ignores case AC-213`() {
        assertThat(ProfileRules.copyName("Balanced", listOf("balanced COPY"))).isEqualTo("Balanced copy 2")
    }

    @Test
    fun `empty or blank name is name-required AC-214`() {
        assertThat(validate(draft(name = ""))).containsExactly(FieldError("name", "name-required"))
        assertThat(validate(draft(name = "   "))).containsExactly(FieldError("name", "name-required"))
    }

    @Test
    fun `name is trimmed before rules and 40 chars is accepted AC-214`() {
        assertThat(validate(draft(name = "  Cheap vision  "), own = listOf("Other"))).isEmpty()
        assertThat(validate(draft(name = "x".repeat(40)))).isEmpty()
        assertThat(validate(draft(name = " " + "x".repeat(40) + " "))).isEmpty()
    }

    @Test
    fun `41 chars after trimming is name-too-long AC-214`() {
        assertThat(validate(draft(name = "x".repeat(41)))).containsExactly(FieldError("name", "name-too-long"))
    }

    @Test
    fun `name equal to another own profile ignoring case is name-taken AC-214`() {
        assertThat(validate(draft(name = "cheap VISION"), own = listOf("Cheap vision")))
            .containsExactly(FieldError("name", "name-taken"))
    }

    @Test
    fun `system names are reserved ignoring case AC-214`() {
        assertThat(validate(draft(name = "balanced"))).containsExactly(FieldError("name", "name-reserved"))
        assertThat(validate(draft(name = "Fast"))).containsExactly(FieldError("name", "name-reserved"))
        assertThat(validate(draft(name = " CAREFUL "))).containsExactly(FieldError("name", "name-reserved"))
        assertThat(validate(draft(name = "fast and CHEAP"))).containsExactly(FieldError("name", "name-reserved"))
    }

    @Test
    fun `empty text slot is text-slot-required AC-215`() {
        assertThat(validate(draft(text = emptyList())))
            .containsExactly(FieldError("slots.text", "text-slot-required"))
    }

    @Test
    fun `text-only model in vision slot is model-not-capable at its index AC-216`() {
        assertThat(validate(draft(vision = ids("v1", "t1"))))
            .containsExactly(FieldError("slots.vision[1]", "model-not-capable"))
    }

    @Test
    fun `non image-producing model in image slot is model-not-capable AC-216`() {
        assertThat(validate(draft(image = ids("t2"))))
            .containsExactly(FieldError("slots.image[0]", "model-not-capable"))
    }

    @Test
    fun `image generator in text slot is model-not-capable AC-216`() {
        assertThat(validate(draft(text = ids("t1", "i1"))))
            .containsExactly(FieldError("slots.text[1]", "model-not-capable"))
    }

    @Test
    fun `fourth model is slot-full at index 3 AC-217`() {
        assertThat(validate(draft(text = ids("t1", "t2", "t3", "t4"))))
            .containsExactly(FieldError("slots.text[3]", "slot-full"))
    }

    @Test
    fun `three models are accepted AC-217`() {
        assertThat(validate(draft(text = ids("t1", "t2", "t3")))).isEmpty()
    }

    @Test
    fun `same model twice is model-duplicate at the second index AC-217`() {
        assertThat(validate(draft(text = ids("t1", "t2", "t1"))))
            .containsExactly(FieldError("slots.text[2]", "model-duplicate"))
    }

    @Test
    fun `same model may appear in different slots AC-217`() {
        assertThat(validate(draft(text = ids("v1"), vision = ids("v1")))).isEmpty()
    }

    @Test
    fun `twenty custom profiles block create and duplicate AC-218`() {
        assertThat(ProfileRules.MAX_CUSTOM_PROFILES).isEqualTo(20)
        assertThat(ProfileRules.canCreate(19)).isTrue()
        assertThat(ProfileRules.canCreate(20)).isFalse()
        assertThat(ProfileRules.canCreate(21)).isFalse()
    }

    @Test
    fun `system profiles are not editable but custom ones are AC-219`() {
        SystemProfileKey.entries.forEach {
            assertThat(ProfileRules.isEditable(ProfileRef.System(it))).isFalse()
        }
        assertThat(ProfileRules.isEditable(ProfileRef.Custom(ModelProfileId(Uuid7.next())))).isTrue()
    }

    @Test
    fun `system key wire values are lowercase keys AC-219`() {
        assertThat(SystemProfileKey.entries.map { it.wire }).containsExactly("fast", "balanced", "careful")
    }

    @Test
    fun `newly added model missing from catalog is model-left-catalog AC-221`() {
        val cat = catalog - ModelId("t2")
        assertThat(
            validate(draft(text = ids("t1", "t2")), baseline = mapOf(ModelSlotKind.TEXT to ids("t1")), cat = cat),
        ).containsExactly(FieldError("slots.text[1]", "model-left-catalog"))
    }

    @Test
    fun `older baseline model missing from catalog is kept without error AC-221`() {
        val cat = catalog - ModelId("t2")
        val d = draft(text = ids("t2", "t1"))
        assertThat(validate(d, baseline = mapOf(ModelSlotKind.TEXT to ids("t2")), cat = cat)).isEmpty()
    }

    @Test
    fun `model absent from catalog in a new profile without baseline is model-left-catalog AC-221`() {
        assertThat(validate(draft(text = ids("gone"))))
            .containsExactly(FieldError("slots.text[0]", "model-left-catalog"))
    }

    @Test
    fun `several broken rules are all reported together`() {
        val d = draft(name = "", text = emptyList(), vision = ids("t1"))
        assertThat(validate(d)).containsExactlyInAnyOrder(
            FieldError("name", "name-required"),
            FieldError("slots.text", "text-slot-required"),
            FieldError("slots.vision[0]", "model-not-capable"),
        )
    }
}
