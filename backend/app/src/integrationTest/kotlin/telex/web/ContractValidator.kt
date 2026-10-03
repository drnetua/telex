package telex.web

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.SimpleRequest
import com.atlassian.oai.validator.model.SimpleResponse
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.net.http.HttpResponse

/** Checks real HTTP exchanges against `contracts/openapi.yaml`, so contract drift fails a test (test-plan.md). */
object ContractValidator {
    const val SKELETON_SPEC = "docs/features/platform-skeleton/contracts/openapi.yaml"
    const val APP_SHELL_SPEC = "docs/features/app-shell/contracts/openapi.yaml"
    private val REFUSED_REQUEST = setOf(400, 403)

    private val validators = java.util.concurrent.ConcurrentHashMap<String, OpenApiInteractionValidator>()

    private fun validatorFor(specPath: String): OpenApiInteractionValidator =
        validators.computeIfAbsent(specPath) { load(it) }

    private fun load(specPath: String): OpenApiInteractionValidator {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, specPath).isFile) dir = dir.parentFile
        val spec = File(checkNotNull(dir) { "$specPath not found above ${File("").absolutePath}" }, specPath)
        return OpenApiInteractionValidator.createFor(spec.toURI().toString()).build()
    }

    /** Fails with the validator's messages when the request or the response does not match the contract. */
    fun assertConforms(
        method: String,
        path: String,
        requestBody: String?,
        requestHeaders: Map<String, String>,
        response: HttpResponse<String>,
        specPath: String = SKELETON_SPEC,
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
        val report = validatorFor(specPath).validate(request.build(), reply.build())
        // A 400 or 403 answers a deliberately malformed request (bad body, missing CSRF header), so only its
        // response has to match the contract.
        val relevant =
            report.messages.filter {
                response.statusCode() !in REFUSED_REQUEST ||
                    !it.key.startsWith("validation.request.")
            }
        assertThat(relevant.map { "${it.key}: ${it.message}" })
            .describedAs("$method $path -> ${response.statusCode()} must match openapi.yaml")
            .isEmpty()
    }
}
