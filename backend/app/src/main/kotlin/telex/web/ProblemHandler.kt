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
 * Domain errors extend [telex.shared.DomainProblem]; framework errors get a code derived from their status.
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
                FieldProblem(it.field, it.code ?: "invalid", it.defaultMessage.orEmpty())
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
            val code = HttpStatus.resolve(statusCode.value())?.name?.lowercase() ?: "http_${statusCode.value()}"
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

    companion object {
        const val VALIDATION_FAILED = "validation_failed"
        const val INTERNAL_ERROR = "internal_error"
    }
}
