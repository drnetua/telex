package telex.web

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.SimpleRequest
import com.atlassian.oai.validator.model.SimpleResponse
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.net.http.HttpResponse

/** Checks real HTTP exchanges against `contracts/openapi.yaml`, so contract drift fails a test (test-plan.md). */
object ContractValidator {
    private const val SPEC = "docs/features/platform-skeleton/contracts/openapi.yaml"

    private val validator: OpenApiInteractionValidator by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, SPEC).isFile) dir = dir.parentFile
        val spec = File(checkNotNull(dir) { "$SPEC not found above ${File("").absolutePath}" }, SPEC)
        OpenApiInteractionValidator.createFor(spec.toURI().toString()).build()
    }

    /** Fails with the validator's messages when the request or the response does not match the contract. */
    fun assertConforms(
        method: String,
        path: String,
        requestBody: String?,
        requestHeaders: Map<String, String>,
        response: HttpResponse<String>,
    ) {
        val request =
            SimpleRequest.Builder(method, path.substringBefore('?')).apply {
                requestHeaders.forEach { (k, v) -> withHeader(k, v) }
                if (!requestBody.isNullOrEmpty()) withBody(requestBody)
                path.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach {
                    withQueryParam(it.substringBefore('='), it.substringAfter('=', ""))
                }
            }
        val reply =
            SimpleResponse.Builder(response.statusCode()).apply {
                response.headers().map().forEach { (k, values) -> values.forEach { withHeader(k, it) } }
                if (response.body().isNotEmpty()) withBody(response.body())
            }
        val report = validator.validate(request.build(), reply.build())
        // A 400 answers a deliberately malformed request, so only its response has to match the contract.
        val relevant =
            report.messages.filter {
                response.statusCode() != 400 ||
                    !it.key.startsWith("validation.request.")
            }
        assertThat(relevant.map { "${it.key}: ${it.message}" })
            .describedAs("$method $path -> ${response.statusCode()} must match openapi.yaml")
            .isEmpty()
    }
}
