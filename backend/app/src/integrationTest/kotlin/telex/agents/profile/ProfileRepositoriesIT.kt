package telex.agents.profile

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import telex.TestcontainersConfiguration
import telex.agents.ModelProfileId
import telex.agents.ModelSlotKind
import telex.agents.ProfileRef
import telex.agents.SystemProfileKey
import telex.agents.internal.profile.DefaultProfileRepository
import telex.agents.internal.profile.ModelProfile
import telex.agents.internal.profile.ProfileRepository
import telex.agents.internal.profile.ProfileRules
import telex.identity.OwnerId
import telex.llm.ModelId
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.Future

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ProfileRepositoriesIT {
    @Autowired lateinit var profiles: ProfileRepository

    @Autowired lateinit var defaults: DefaultProfileRepository

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var tx: PlatformTransactionManager

    private val now = Instant.parse("2026-10-03T10:00:00Z")

    // This context is shared with identity ITs that wipe `owner`; leave no rows that reference an Owner.
    @AfterEach
    fun clearProfiles() {
        jdbc.update("DELETE FROM default_model_profile")
        jdbc.update("DELETE FROM model_profile")
    }

    private fun owner(): OwnerId {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        return OwnerId(id)
    }

    private fun m(s: String) = ModelId(s)

    private fun profile(
        name: String,
        text: List<String> = listOf("a/one", "b/two", "c/three"),
        vision: List<String> = emptyList(),
        image: List<String> = emptyList(),
    ) = ModelProfile(
        ModelProfileId(UUID.randomUUID()),
        name,
        mapOf(
            ModelSlotKind.TEXT to text.map(::m),
            ModelSlotKind.VISION to vision.map(::m),
            ModelSlotKind.IMAGE to image.map(::m),
        ),
    )

    @Test
    fun `insert then find returns the profile with its chains in order and empty slots as empty lists`() {
        val o = owner()
        val p = profile("Cheap vision", vision = listOf("v/one", "v/two"))
        profiles.insert(o, p, now)
        val found = profiles.find(o, p.id)!!
        assertThat(found.name).isEqualTo("Cheap vision")
        assertThat(found.slots[ModelSlotKind.TEXT]).containsExactly(m("a/one"), m("b/two"), m("c/three"))
        assertThat(found.slots[ModelSlotKind.VISION]).containsExactly(m("v/one"), m("v/two"))
        assertThat(found.slots[ModelSlotKind.IMAGE] ?: emptyList()).isEmpty()
        assertThat(
            jdbc.queryForList(
                "SELECT position FROM model_profile_slot_model WHERE model_profile_id = ? " +
                    "AND slot = 'text' ORDER BY position",
                Int::class.java,
                p.id.value,
            ),
        ).containsExactly(1, 2, 3)
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM model_profile_slot_model WHERE model_profile_id = ? AND slot = 'image'",
                Int::class.java,
                p.id.value,
            ),
        ).isZero()
    }

    @Test
    fun `update replaces the chains incl a reorder and renames, and reports false for a missing profile`() {
        val o = owner()
        val p = profile("Night", text = listOf("a/one", "b/two", "c/three"), vision = listOf("v/one"))
        profiles.insert(o, p, now)
        val edited =
            p.copy(
                name = "Day",
                slots =
                    mapOf(
                        ModelSlotKind.TEXT to listOf(m("c/three"), m("a/one")),
                        ModelSlotKind.VISION to emptyList(),
                        ModelSlotKind.IMAGE to listOf(m("i/one")),
                    ),
            )
        assertThat(profiles.update(o, edited)).isTrue()
        val found = profiles.find(o, p.id)!!
        assertThat(found.name).isEqualTo("Day")
        assertThat(found.slots[ModelSlotKind.TEXT]).containsExactly(m("c/three"), m("a/one"))
        assertThat(found.slots[ModelSlotKind.VISION] ?: emptyList()).isEmpty()
        assertThat(found.slots[ModelSlotKind.IMAGE]).containsExactly(m("i/one"))
        assertThat(profiles.update(o, profile("Ghost"))).isFalse()
    }

    @Test
    fun `list is the owner's profiles oldest first with chains, and count and names match`() {
        val o = owner()
        val second = profile("Second", vision = listOf("v/one"))
        val first = profile("First")
        profiles.insert(o, second, now.plusSeconds(10))
        profiles.insert(o, first, now)
        assertThat(profiles.list(o).map { it.name }).containsExactly("First", "Second")
        assertThat(profiles.list(o).last().slots[ModelSlotKind.VISION]).containsExactly(m("v/one"))
        assertThat(profiles.count(o)).isEqualTo(2)
        assertThat(profiles.names(o)).containsExactlyInAnyOrder("First", "Second")
        val empty = owner()
        assertThat(profiles.list(empty)).isEmpty()
        assertThat(profiles.count(empty)).isZero()
        assertThat(profiles.names(empty)).isEmpty()
    }

    @Test
    fun `AC-222 another owner never sees, finds, changes or deletes the profile`() {
        val a = owner()
        val b = owner()
        val p = profile("Night shift")
        profiles.insert(a, p, now)
        assertThat(profiles.find(b, p.id)).isNull()
        assertThat(profiles.find(b, ModelProfileId(UUID.randomUUID()))).isNull()
        assertThat(profiles.list(b)).isEmpty()
        assertThat(profiles.count(b)).isZero()
        assertThat(profiles.names(b)).isEmpty()
        assertThat(profiles.update(b, p.copy(name = "Hijacked"))).isFalse()
        assertThat(profiles.delete(b, p.id)).isFalse()
        assertThat(defaults.clearIfCustom(b, p.id)).isFalse()
        val untouched = profiles.find(a, p.id)!!
        assertThat(untouched.name).isEqualTo("Night shift")
        assertThat(untouched.slots[ModelSlotKind.TEXT]).hasSize(3)
    }

    @Test
    fun `AC-222 making another owner's profile the default is refused and stores nothing`() {
        val a = owner()
        val b = owner()
        val p = profile("Night shift")
        profiles.insert(a, p, now)
        assertThatThrownBy { defaults.set(b, ProfileRef.Custom(p.id), now) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThat(defaults.get(b)).isNull()
    }

    @Test
    fun `names are unique per owner case-insensitively but free across owners`() {
        val a = owner()
        val b = owner()
        profiles.insert(a, profile("Night"), now)
        assertThatThrownBy { profiles.insert(a, profile("NIGHT"), now) }
            .isInstanceOf(DuplicateKeyException::class.java)
        profiles.insert(b, profile("Night"), now)
        val other = profile("Day")
        profiles.insert(a, other, now)
        assertThatThrownBy { profiles.update(a, other.copy(name = "night")) }
            .isInstanceOf(DuplicateKeyException::class.java)
        assertThat(profiles.find(a, other.id)!!.name).isEqualTo("Day")
    }

    @Test
    fun `delete removes the profile and its chains and says false when it is not the default`() {
        val o = owner()
        val p = profile("Temp", vision = listOf("v/one"))
        profiles.insert(o, p, now)
        assertThat(defaults.clearIfCustom(o, p.id)).isFalse()
        assertThat(profiles.delete(o, p.id)).isTrue()
        assertThat(profiles.find(o, p.id)).isNull()
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM model_profile_slot_model WHERE model_profile_id = ?",
                Int::class.java,
                p.id.value,
            ),
        ).isZero()
        assertThat(profiles.delete(o, p.id)).isFalse()
    }

    @Test
    fun `AC-220 deleting the default custom profile reverts the default to Balanced`() {
        val o = owner()
        val p = profile("Cheap vision")
        profiles.insert(o, p, now)
        defaults.set(o, ProfileRef.Custom(p.id), now)
        assertThat(defaults.get(o)).isEqualTo(ProfileRef.Custom(p.id))
        val wasDefault =
            TransactionTemplate(tx).execute {
                val cleared = defaults.clearIfCustom(o, p.id)
                profiles.delete(o, p.id)
                cleared
            }
        assertThat(wasDefault).isTrue()
        assertThat(profiles.list(o)).isEmpty()
        assertThat(defaults.get(o)).isNull()
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM default_model_profile WHERE owner_id = ?",
                Int::class.java,
                o.value,
            ),
        ).isZero()
    }

    @Test
    fun `deleting a profile that is not the default leaves the default alone`() {
        val o = owner()
        val p = profile("Keep")
        val q = profile("Drop")
        profiles.insert(o, p, now)
        profiles.insert(o, q, now)
        defaults.set(o, ProfileRef.Custom(p.id), now)
        assertThat(defaults.clearIfCustom(o, q.id)).isFalse()
        assertThat(profiles.delete(o, q.id)).isTrue()
        assertThat(defaults.get(o)).isEqualTo(ProfileRef.Custom(p.id))
    }

    @Test
    fun `default get set upsert and Balanced deletes the row`() {
        val o = owner()
        assertThat(defaults.get(o)).isNull()
        defaults.set(o, ProfileRef.System(SystemProfileKey.CAREFUL), now)
        assertThat(defaults.get(o)).isEqualTo(ProfileRef.System(SystemProfileKey.CAREFUL))
        val p = profile("Mine")
        profiles.insert(o, p, now)
        defaults.set(o, ProfileRef.Custom(p.id), now.plusSeconds(1))
        assertThat(defaults.get(o)).isEqualTo(ProfileRef.Custom(p.id))
        defaults.set(o, ProfileRef.System(SystemProfileKey.FAST), now.plusSeconds(2))
        assertThat(defaults.get(o)).isEqualTo(ProfileRef.System(SystemProfileKey.FAST))
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM default_model_profile WHERE owner_id = ?",
                Int::class.java,
                o.value,
            ),
        ).isEqualTo(1)
        defaults.set(o, ProfileRef.System(SystemProfileKey.BALANCED), now.plusSeconds(3))
        assertThat(defaults.get(o)).isNull()
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM default_model_profile WHERE owner_id = ?",
                Int::class.java,
                o.value,
            ),
        ).isZero()
        // setting Balanced when nothing is stored is a no-op, not an error
        defaults.set(o, ProfileRef.System(SystemProfileKey.BALANCED), now.plusSeconds(4))
        assertThat(defaults.get(o)).isNull()
    }

    @Test
    fun `default is per owner`() {
        val a = owner()
        val b = owner()
        defaults.set(a, ProfileRef.System(SystemProfileKey.FAST), now)
        assertThat(defaults.get(b)).isNull()
    }

    @Test
    fun `AC-218 two parallel creates at 19 profiles under the owner lock admit exactly one`() {
        val o = owner()
        repeat(
            ProfileRules.MAX_CUSTOM_PROFILES - 1,
        ) { profiles.insert(o, profile("P$it"), now.plusSeconds(it.toLong())) }
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results: List<Future<Boolean>> =
                (1..2).map { n ->
                    pool.submit<Boolean> {
                        TransactionTemplate(tx).execute {
                            barrier.await() // both transactions are open before either takes the lock
                            profiles.lockOwner(o)
                            val canCreate = ProfileRules.canCreate(profiles.count(o))
                            Thread.sleep(RACE_WINDOW_MS) // without the lock, both would see 19 here
                            if (canCreate) {
                                profiles.insert(o, profile("Racer $n"), now)
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            // the loser waits on the lock until the winner commits, then counts 20
            val outcomes = results.map { runCatching { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull() }
            assertThat(outcomes.count { it == true }).isEqualTo(1)
            assertThat(profiles.count(o)).isEqualTo(ProfileRules.MAX_CUSTOM_PROFILES)
        } finally {
            pool.shutdownNow()
        }
    }
}

private const val RACE_WINDOW_MS = 300L
