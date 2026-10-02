package telex.identity

import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.CredentialRecord
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType
import telex.identity.internal.passkey.neverUsed
import java.time.Instant

/**
 * Spring Security's own test fixture is not published, so this is the entry point for the passkey tests: a credential
 * record builder with valid defaults, null-friendly for the dates (a never-used passkey has no last-used date).
 */
object TestCredentialRecords {
    fun userCredential(): Builder = Builder()

    class Builder {
        private val delegate =
            ImmutableCredentialRecord
                .builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(Bytes.random())
                .userEntityUserId(Bytes.random())
                .publicKey(ImmutablePublicKeyCose(ByteArray(PUBLIC_KEY_BYTES) { it.toByte() }))
                .signatureCount(0)
                .uvInitialized(false)
                .backupEligible(false)
                .backupState(false)
                .label("Safari on iPhone")
        private var lastUsed: Instant? = null

        fun userEntityUserId(id: Bytes) = apply { delegate.userEntityUserId(id) }

        fun publicKey(key: ByteArray) = apply { delegate.publicKey(ImmutablePublicKeyCose(key)) }

        fun attestationObject(bytes: ByteArray) = apply { delegate.attestationObject(Bytes(bytes)) }

        fun credentialId(id: Bytes) = apply { delegate.credentialId(id) }

        fun created(created: Instant?) = apply { created?.let { delegate.created(it) } }

        fun lastUsed(lastUsed: Instant?) = apply { this.lastUsed = lastUsed }

        fun build(): CredentialRecord {
            val used = lastUsed
            if (used != null) return delegate.lastUsed(used).build()
            return neverUsed(delegate.build())
        }
    }

    private const val PUBLIC_KEY_BYTES = 32
}
