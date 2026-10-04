package telex.agents

import org.springframework.http.HttpStatus
import telex.shared.DomainProblem
import telex.shared.FieldProblem

class ProfileInvalid(
    errors: List<FieldProblem>,
) : DomainProblem(HttpStatus.BAD_REQUEST, "validation-failed", "Some fields are invalid.", errors)

class SystemProfileReadOnly :
    DomainProblem(HttpStatus.CONFLICT, "system-profile-read-only", "System profiles can't be changed.")

class NoTextModel : DomainProblem(HttpStatus.CONFLICT, "no-text-model", "This profile has no model available for text.")

data class ProfileDeletion(
    val defaultProfile: ProfileRef,
    val defaultReset: Boolean,
)
