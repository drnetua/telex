package telex.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import telex.TestcontainersConfiguration
import telex.identity.internal.owner.Owners
import telex.shared.DomainProblem
import telex.shared.FieldProblem
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** AC-179 (theme saved), AC-183 (save-if-unset), AC-184 (change zone), AC-186 (never empty). */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OwnerPreferencesIT {
    @Autowired lateinit var preferences: OwnerPreferences

    @Autowired lateinit var profiles: OwnerProfiles

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun reset() {
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM owner")
    }

    private fun anOwner(email: String = "anton@mail.com"): OwnerId =
        owners.findOrCreate(email, email, Instant.parse("2026-10-02T10:00:00Z")).first

    private fun anOwnerWithTimeZone(zone: String = "Europe/Kyiv"): OwnerId =
        anOwner().also { preferences.saveDetectedTimeZone(it, zone) }

    private fun refusedFieldCode(zone: String?): String {
        val thrown = runCatching { preferences.changeTimeZone(anOwnerWithTimeZone(), zone) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(DomainProblem::class.java)
        val body = (thrown as DomainProblem).body
        assertThat(body.properties?.get("code")).isEqualTo("validation-failed")
        return (body.properties?.get("errors") as List<*>).filterIsInstance<FieldProblem>().single().code
    }

    @Test
    fun `a new owner has the system theme and no timezone`() {
        val owner = anOwner()
        val prefs = preferences.of(owner)!!
        assertThat(prefs.theme).isEqualTo(Theme.SYSTEM)
        assertThat(prefs.timeZone).isNull()
        assertThat(prefs.timeZoneIsFallback).isFalse()
        assertThat(preferences.timeZoneOf(owner)).isNull()
    }

    @Test
    fun `changing the theme is saved and shown on me`() {
        val owner = anOwner()
        preferences.changeTheme(owner, Theme.DARK)
        assertThat(preferences.of(owner)!!.theme).isEqualTo(Theme.DARK)
        assertThat(profiles.me(owner)!!.theme).isEqualTo(Theme.DARK)
    }

    @Test
    fun `the first detected zone on the list is saved`() {
        val owner = anOwner()
        val saved = preferences.saveDetectedTimeZone(owner, "Europe/Kyiv")
        assertThat(saved.timeZone).isEqualTo("Europe/Kyiv")
        assertThat(saved.timeZoneIsFallback).isFalse()
        assertThat(profiles.me(owner)!!.timeZone).isEqualTo("Europe/Kyiv")
    }

    @Test
    fun `an undetectable or unknown detected zone falls back to UTC`() {
        listOf(null, "", "  ", "Europe/Atlantis").forEachIndexed { i, zone ->
            val owner = anOwner("fallback$i@mail.com")
            val saved = preferences.saveDetectedTimeZone(owner, zone)
            assertThat(saved.timeZone).isEqualTo("UTC")
            assertThat(saved.timeZoneIsFallback).isTrue()
        }
    }

    @Test
    fun `a later detected zone never overwrites a saved one`() {
        val owner = anOwnerWithTimeZone("Europe/Kyiv")
        val again = preferences.saveDetectedTimeZone(owner, "Asia/Tokyo")
        assertThat(again.timeZone).isEqualTo("Europe/Kyiv")
        assertThat(preferences.saveDetectedTimeZone(owner, null).timeZone).isEqualTo("Europe/Kyiv")
        assertThat(preferences.timeZoneOf(owner)).isEqualTo("Europe/Kyiv")
    }

    @Test
    fun `two racing first saves leave exactly one zone and both see it`() {
        val owner = anOwner()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val calls =
                listOf("Europe/Kyiv", "Asia/Tokyo").map { zone ->
                    pool.submit(
                        Callable {
                            start.await()
                            preferences.saveDetectedTimeZone(owner, zone)
                        },
                    )
                }
            start.countDown()
            val results = calls.map { it.get() }
            val stored = preferences.timeZoneOf(owner)
            assertThat(stored).isIn("Europe/Kyiv", "Asia/Tokyo")
            assertThat(results.map { it.timeZone }).containsOnly(stored)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `picking a zone while on the UTC fallback saves it and clears the flag`() {
        val owner = anOwner()
        preferences.saveDetectedTimeZone(owner, null)
        val changed = preferences.changeTimeZone(owner, "Asia/Tokyo")
        assertThat(changed.timeZone).isEqualTo("Asia/Tokyo")
        assertThat(changed.timeZoneIsFallback).isFalse()
        assertThat(preferences.of(owner)!!.timeZoneIsFallback).isFalse()
    }

    @Test
    fun `picking UTC explicitly clears the fallback flag`() {
        val owner = anOwner()
        preferences.saveDetectedTimeZone(owner, null)
        val changed = preferences.changeTimeZone(owner, "UTC")
        assertThat(changed.timeZone).isEqualTo("UTC")
        assertThat(changed.timeZoneIsFallback).isFalse()
    }

    @Test
    fun `an empty zone is refused as required and the saved one is kept`() {
        val owner = anOwnerWithTimeZone("Europe/Kyiv")
        listOf(null, "", "   ").forEach { zone ->
            val thrown = runCatching { preferences.changeTimeZone(owner, zone) }.exceptionOrNull()
            assertThat(thrown).isInstanceOf(DomainProblem::class.java)
        }
        assertThat(preferences.timeZoneOf(owner)).isEqualTo("Europe/Kyiv")
        assertThat(refusedFieldCode("")).isEqualTo("time-zone-required")
        assertThat(refusedFieldCode(null)).isEqualTo("time-zone-required")
    }

    @Test
    fun `an unknown zone is refused and the saved one is kept`() {
        val owner = anOwnerWithTimeZone("Europe/Kyiv")
        val thrown = runCatching { preferences.changeTimeZone(owner, "Europe/Atlantis") }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(DomainProblem::class.java)
        assertThat(preferences.timeZoneOf(owner)).isEqualTo("Europe/Kyiv")
        assertThat(refusedFieldCode("Europe/Atlantis")).isEqualTo("unknown-time-zone")
    }
}
