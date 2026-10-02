package telex.web

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import telex.shared.DomainProblem

@WebMvcTest(ProblemHandlerTest.ProbeController::class)
@Import(ProblemHandlerTest.ProbeController::class)
class ProblemHandlerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `domain problems render as problem+json with their code`() {
        mockMvc.get("/probe/domain").andExpect {
            status { isConflict() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("urn:telex:error:probe-conflict") }
            jsonPath("$.code") { value("probe-conflict") }
            jsonPath("$.status") { value(409) }
            jsonPath("$.detail") { value("Probe is in conflict.") }
        }
    }

    @Test
    fun `invalid fields render in errors`() {
        mockMvc
            .post("/probe/validated") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": ""}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value(ProblemHandler.VALIDATION_FAILED) }
                jsonPath("$.errors[0].field") { value("name") }
                jsonPath("$.errors[0].code") { value("required") }
            }
    }

    @Test
    fun `framework errors use a code from the contract, never one derived from the status name`() {
        mockMvc.post("/probe/domain").andExpect {
            status { isMethodNotAllowed() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("urn:telex:error:validation-failed") }
            jsonPath("$.code") { value("validation-failed") }
        }
        mockMvc
            .post("/probe/validated") {
                contentType = MediaType.TEXT_PLAIN
                content = "name"
            }.andExpect {
                status { isUnsupportedMediaType() }
                jsonPath("$.code") { value("validation-failed") }
            }
    }

    @Test
    fun `malformed or empty JSON is 400 validation-failed`() {
        listOf("{not json", "").forEach { raw ->
            mockMvc
                .post("/probe/validated") {
                    contentType = MediaType.APPLICATION_JSON
                    content = raw
                }.andExpect {
                    status { isBadRequest() }
                    content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                    jsonPath("$.code") { value(ProblemHandler.VALIDATION_FAILED) }
                }
        }
    }

    @Test
    fun `unexpected errors render as internal-error without leaking details`() {
        mockMvc.get("/probe/crash").andExpect {
            status { isInternalServerError() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value(ProblemHandler.INTERNAL_ERROR) }
            jsonPath("$.detail") { value("Something went wrong on our side.") }
        }
    }

    @RestController
    class ProbeController {
        @GetMapping("/probe/domain")
        fun domain(): Nothing = throw DomainProblem(HttpStatus.CONFLICT, "probe-conflict", "Probe is in conflict.")

        @PostMapping("/probe/validated")
        fun validated(
            @Valid @RequestBody body: ProbeBody,
        ): ProbeBody = body

        @GetMapping("/probe/crash")
        fun crash(): Nothing = error("secret internal state")
    }

    data class ProbeBody(
        @field:NotBlank val name: String,
    )
}
