package telex.web

import com.webauthn4j.converter.AttestationObjectConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.attestation.AttestationObject
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import org.springframework.security.web.webauthn.api.Bytes
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * A minimal software passkey for tests: an ES256 key pair that signs real WebAuthn assertions, so the relying party's
 * own verification runs against it instead of being stubbed.
 */
class SoftwareAuthenticator(
    private val rpId: String = "localhost",
    private val origin: String = "http://localhost:8080",
) {
    val credentialId: Bytes = Bytes.random()

    private val keys =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()

    private val coseKey = EC2COSEKey.create(keys.public as ECPublicKey, COSEAlgorithmIdentifier.ES256)

    private val objectConverter = ObjectConverter()

    /** The public key as the COSE structure the credential store holds. */
    val publicKeyCose: ByteArray = objectConverter.cborConverter.writeValueAsBytes(coseKey)

    /** The "none" attestation object the framework keeps with a registered credential and reads back on sign-in. */
    val attestationObject: ByteArray =
        AttestationObjectConverter(objectConverter).convertToBytes(
            AttestationObject(
                AuthenticatorData(
                    MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray()),
                    ATTESTED_USER_PRESENT,
                    0,
                    AttestedCredentialData(AAGUID.ZERO, credentialId.bytes, coseKey),
                ),
                NoneAttestationStatement(),
            ),
        )

    private var signCount = 0

    /** The JSON body of `POST /login/webauthn` answering [challenge] (Base64URL), for the user entity [userHandle]. */
    fun assertion(
        challenge: String,
        userHandle: Bytes,
    ): String {
        val clientData =
            """{"type":"webauthn.get","challenge":"$challenge","origin":"$origin","crossOrigin":false}"""
                .toByteArray()
        val authenticatorData = authenticatorData()
        val signature =
            Signature
                .getInstance("SHA256withECDSA")
                .apply {
                    initSign(keys.private)
                    update(authenticatorData)
                    update(MessageDigest.getInstance("SHA-256").digest(clientData))
                }.sign()
        val id = credentialId.toBase64UrlString()
        return """
            {"id":"$id","rawId":"$id","type":"public-key","authenticatorAttachment":"platform",
             "clientExtensionResults":{},
             "response":{"authenticatorData":"${Bytes(authenticatorData).toBase64UrlString()}",
                         "clientDataJSON":"${Bytes(clientData).toBase64UrlString()}",
                         "signature":"${Bytes(signature).toBase64UrlString()}",
                         "userHandle":"${userHandle.toBase64UrlString()}"}}
            """.trimIndent()
    }

    private fun authenticatorData(): ByteArray {
        val rpIdHash = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray())
        signCount += 1
        return ByteBuffer
            .allocate(RP_ID_HASH_BYTES + FLAGS_BYTES + COUNTER_BYTES)
            .put(rpIdHash)
            .put(USER_PRESENT_AND_VERIFIED)
            .putInt(signCount)
            .array()
    }

    private companion object {
        const val RP_ID_HASH_BYTES = 32
        const val FLAGS_BYTES = 1
        const val COUNTER_BYTES = 4
        const val USER_PRESENT_AND_VERIFIED: Byte = 0x05
        const val ATTESTED_USER_PRESENT: Byte = 0x41
    }
}
