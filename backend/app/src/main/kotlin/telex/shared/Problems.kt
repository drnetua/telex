package telex.shared

import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import java.net.URI

/** One invalid field, rendered in the `errors[]` extension of a problem. */
data class FieldProblem(
    val field: String,
    val code: String,
    val message: String,
)

/**
 * RFC 9457 problem with the teleX extensions: `type = urn:telex:error:<code>`, `code` (stable machine code that
 * keys the UI message catalog) and, when present, `errors[]`.
 */
fun problemDetail(
    status: HttpStatusCode,
    code: String,
    detail: String,
    errors: List<FieldProblem> = emptyList(),
): ProblemDetail =
    ProblemDetail.forStatusAndDetail(status, detail).apply {
        type = URI.create("urn:telex:error:$code")
        setProperty("code", code)
        if (errors.isNotEmpty()) setProperty("errors", errors)
    }

/** Base for domain errors; the `web` problem handler renders it as `application/problem+json`. */
open class DomainProblem(
    status: HttpStatusCode,
    code: String,
    detail: String,
    errors: List<FieldProblem> = emptyList(),
    cause: Throwable? = null,
) : ErrorResponseException(status, problemDetail(status, code, detail, errors), cause)
