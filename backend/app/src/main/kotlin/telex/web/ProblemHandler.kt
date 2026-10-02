package telex.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import telex.shared.FieldProblem
import telex.shared.problemDetail
import java.net.URI

/**
 * Renders every error as RFC 9457 `application/problem+json` with the teleX `code` / `errors[]` extensions.
 * Domain errors extend [telex.shared.DomainProblem]; framework errors get the contract code for their status class.
 */
@RestControllerAdvice
class ProblemHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors =
            ex.bindingResult.fieldErrors.map {
                FieldProblem(it.field, fieldCode(it.code, it.field), it.defaultMessage.orEmpty())
            }
        val body = problemDetail(status, VALIDATION_FAILED, "Some fields are invalid.", errors)
        return handleExceptionInternal(ex, body, headers, status, request)
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val response = super.handleExceptionInternal(ex, body, headers, statusCode, request)
        val problem = response?.body
        if (problem is ProblemDetail && problem.properties?.containsKey("code") != true) {
            val code = contractCode(statusCode)
            problem.type = URI.create("urn:telex:error:$code")
            problem.setProperty("code", code)
        }
        return response
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unhandled error", ex)
        return problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, "Something went wrong on our side.")
    }

    /** Only codes in the contract's `ErrorCode` enum may be emitted. */
    private fun contractCode(status: HttpStatusCode): String =
        when (status.value()) {
            HttpStatus.UNAUTHORIZED.value() -> "unauthenticated"
            HttpStatus.FORBIDDEN.value() -> "forbidden"
            HttpStatus.NOT_FOUND.value() -> "not-found"
            else -> if (status.is4xxClientError) VALIDATION_FAILED else INTERNAL_ERROR
        }

    private fun fieldCode(
        constraint: String?,
        field: String,
    ): String =
        when (constraint) {
            "NotBlank", "NotNull", "NotEmpty" -> "required"
            "Pattern", "Email" -> if (field.contains("code", ignoreCase = true)) "code-format" else "email-incomplete"
            else -> constraint?.replace(Regex("([a-z])([A-Z])"), "$1-$2")?.lowercase() ?: "invalid"
        }

    companion object {
        const val VALIDATION_FAILED = "validation-failed"
        const val INTERNAL_ERROR = "internal-error"
    }
}
