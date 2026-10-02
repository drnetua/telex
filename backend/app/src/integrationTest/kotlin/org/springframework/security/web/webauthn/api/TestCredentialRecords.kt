package org.springframework.security.web.webauthn.api

import java.time.Instant

/**
 * Spring Security's own test fixture is not published, so this is the same entry point for [PasskeyCeremoniesIT]:
 * a credential record builder with valid defaults, null-friendly for the dates (a never-used passkey has none).
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

        fun userEntityUserId(id: Bytes) = apply { delegate.userEntityUserId(id) }

        fun created(created: Instant?) = apply { created?.let { delegate.created(it) } }

        /** The builder defaults `lastUsed` to now and types it non-null; a never-used passkey sets the field. */
        fun lastUsed(lastUsed: Instant?) =
            apply {
                if (lastUsed != null) {
                    delegate.lastUsed(lastUsed)
                } else {
                    delegate.javaClass
                        .getDeclaredField("lastUsed")
                        .apply { isAccessible = true }
                        .set(delegate, null)
                }
            }

        fun build(): ImmutableCredentialRecord = delegate.build()
    }

    private const val PUBLIC_KEY_BYTES = 32
}
