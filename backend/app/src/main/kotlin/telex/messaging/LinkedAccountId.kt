package telex.messaging

import telex.shared.TypedId
import java.util.UUID

@JvmInline value class LinkedAccountId(
    override val value: UUID,
) : TypedId

/** The AAD binding a sealed TDLib key to its Linked Account (ADR-0003). */
fun LinkedAccountId.keyAad(): ByteArray = value.toString().toByteArray(Charsets.UTF_8)
