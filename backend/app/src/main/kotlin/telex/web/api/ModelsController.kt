package telex.web.api

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import telex.agents.ModelProfiles
import telex.identity.SignedInOwner
import java.net.URI

@RestController
@RequestMapping("/api/v1/models")
class ModelsController(
    private val models: ModelProfiles,
) {
    @GetMapping("/catalog")
    fun catalog(): ModelCatalogBody = models.catalog().toBody()

    @GetMapping("/profiles")
    fun profiles(
        @AuthenticationPrincipal principal: SignedInOwner,
    ): ModelProfileListBody = models.list(principal.ownerId).toBody()

    @GetMapping("/profiles/{profileKey}")
    fun profile(
        @AuthenticationPrincipal principal: SignedInOwner,
        @PathVariable profileKey: String,
    ): ModelProfileBody = models.get(principal.ownerId, parseProfileKey(profileKey)).toBody()

    @GetMapping("/profile-draft")
    fun draft(
        @AuthenticationPrincipal principal: SignedInOwner,
        @RequestParam(required = false) from: String?,
    ): ModelProfileDraftBody = models.draft(principal.ownerId, from?.let(::parseProfileKey)).toBody()

    @PostMapping("/profiles")
    fun create(
        @AuthenticationPrincipal principal: SignedInOwner,
        @Valid @RequestBody body: ProfileWriteBody,
    ): ResponseEntity<ModelProfileBody> {
        val from = body.duplicatedFrom?.toRef()
        val created = models.create(principal.ownerId, body.name, body.slots.toSlots(), from).toBody()
        return ResponseEntity.created(URI.create("/api/v1/models/profiles/${created.ref.id}")).body(created)
    }

    @PutMapping("/profiles/{profileKey}")
    fun update(
        @AuthenticationPrincipal principal: SignedInOwner,
        @PathVariable profileKey: String,
        @Valid @RequestBody body: ProfileWriteBody,
    ): ModelProfileBody =
        models
            .update(principal.ownerId, parseProfileKey(profileKey), body.name, body.slots.toSlots())
            .toBody()

    @DeleteMapping("/profiles/{profileKey}")
    fun delete(
        @AuthenticationPrincipal principal: SignedInOwner,
        @PathVariable profileKey: String,
    ): ModelProfileDeletionBody = models.delete(principal.ownerId, parseProfileKey(profileKey)).toBody()

    @PutMapping("/default-profile")
    fun setDefault(
        @AuthenticationPrincipal principal: SignedInOwner,
        @Valid @RequestBody body: DefaultProfileChoiceBody,
    ): DefaultProfileBody {
        val ref = checkNotNull(body.profile).toRef()
        return DefaultProfileBody(models.setDefault(principal.ownerId, ref).toBody())
    }
}
