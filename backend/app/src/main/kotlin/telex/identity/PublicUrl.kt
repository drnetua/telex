package telex.identity

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI

/**
 * The one public URL setting (`TELEX_PUBLIC_URL`, ADR-0006): email links are built from it, the cookie is
 * `Secure` when it is https, the WebAuthn RP ID is its host and the allowed origin is its origin.
 */
@Component
class PublicUrl(
    @Value($$"${telex.public-url:http://localhost:8080}") raw: String,
) {
    private val uri = URI.create(raw.trim())

    val host: String = requireNotNull(uri.host) { "telex.public-url needs a host: $raw" }

    val isSecure: Boolean = uri.scheme.equals("https", ignoreCase = true)

    val origin: String =
        buildString {
            append(uri.scheme.lowercase()).append("://").append(host)
            if (uri.port != -1) append(':').append(uri.port)
        }

    fun link(
        path: String,
        fragment: String? = null,
    ): String = origin + "/" + path.trimStart('/') + (fragment?.let { "#$it" } ?: "")
}
