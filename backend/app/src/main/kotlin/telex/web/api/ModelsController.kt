package telex.web.api

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import telex.agents.ModelProfiles
import telex.identity.SignedInOwner

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
}
