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
import telex.identity.internal.passkey.neverUsed
import java.util.UUID

/** Ties a Passkey to exactly one Owner: the WebAuthn user entity's `name` is the `OwnerId` (ADR-0002). */
@Service
class Passkeys(
    private val userEntities: PublicKeyCredentialUserEntityRepository,
    private val credentials: UserCredentialRepository,
    private val owners: Owners,
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

    /** The automatic Passkey name, "<Browser> on <Device>", from the request's User-Agent. */
    fun labelFor(userAgent: String?): String = DeviceLabel.from(userAgent).label
}
