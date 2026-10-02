package telex.identity

import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.CredentialRecord
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import telex.identity.internal.device.DeviceLabel
import telex.identity.internal.owner.Owners
import telex.identity.internal.passkey.PasskeyRows
import telex.identity.internal.passkey.neverUsed
import java.time.Instant
import java.util.UUID

/** Ties a Passkey to exactly one Owner: the WebAuthn user entity's `name` is the `OwnerId` (ADR-0002). */
@Service
class Passkeys(
    private val userEntities: PublicKeyCredentialUserEntityRepository,
    private val credentials: UserCredentialRepository,
    private val owners: Owners,
    private val rows: PasskeyRows,
) {
    /** Finds or creates the Owner's WebAuthn user entity (`name` = OwnerId, `displayName` = email). */
    @Transactional
    fun ensureUserEntity(ownerId: OwnerId) {
        val name = ownerId.value.toString()
        if (userEntities.findByUsername(name) != null) return
        val email = owners.emailOf(ownerId) ?: error("No Owner $name")
        userEntities.save(
            ImmutablePublicKeyCredentialUserEntity
                .builder()
                .id(Bytes.random())
                .name(name)
                .displayName(email)
                .build(),
        )
    }

    /** A just-registered Passkey keeps no last-used date until its first sign-in ("Never used", AC-89). */
    fun registered(record: CredentialRecord): CredentialRecord = neverUsed(record).also(credentials::save)

    /** The Owner a WebAuthn user entity name belongs to, or null if it is not an OwnerId. */
    fun ownerOf(userEntityName: String): OwnerId? = runCatching { OwnerId(UUID.fromString(userEntityName)) }.getOrNull()

    /** The Owner a passkey assertion names, if that Owner still exists. */
    fun existingOwnerOf(userEntityName: String): OwnerId? =
        ownerOf(userEntityName)?.takeIf { owners.emailOf(it) != null }

    /** The automatic Passkey name, "<Browser> on <Device>", from the request's User-Agent. */
    fun labelFor(userAgent: String?): String = DeviceLabel.from(userAgent).label

    /** The Owner's own Passkeys, newest first; empty if they have no user entity yet (AC-89, AC-91, AC-97). */
    fun listMine(ownerId: OwnerId): List<MyPasskey> =
        rows.listFor(ownerId.value.toString()).map { MyPasskey(it.id, it.label, it.createdAt, it.lastUsedAt) }

    /** Removes the Owner's Passkey; false if it is not theirs or does not exist, so both look alike (AC-92, AC-97). */
    @Transactional
    fun removeMine(
        ownerId: OwnerId,
        credentialId: String,
    ): Boolean = rows.deleteFor(ownerId.value.toString(), credentialId)
}

/** A Passkey as shown to its Owner; [id] is the credential id in Base64URL. */
data class MyPasskey(
    val id: String,
    val label: String,
    val createdAt: Instant?,
    val lastUsedAt: Instant?,
)
