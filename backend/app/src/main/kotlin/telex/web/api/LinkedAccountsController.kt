package telex.web.api

import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import telex.identity.SignedInOwner
import telex.messaging.LinkedAccountId
import telex.messaging.LinkedAccountNotFound
import telex.messaging.LinkedAccountSummary
import telex.messaging.LinkedAccounts
import java.time.Instant
import java.util.UUID

data class PhoneBody(
    val countryCode: String,
    val lastDigits: String,
)

data class ChatSyncBody(
    val chatsSynced: Int,
    val chatsTotal: Int?,
    val completedAt: Instant?,
)

data class LinkedAccountItem(
    val id: UUID,
    val displayName: String,
    val phone: PhoneBody,
    val state: String,
    val chatSync: ChatSyncBody,
    val linkedAt: Instant,
)

data class LinkedAccountList(
    val items: List<LinkedAccountItem>,
)

data class UnlinkBody(
    val signOutConfirmed: Boolean,
)

/** The signed-in Owner's own Linked Accounts; another Owner's account looks missing (AC-03). */
@RestController
@RequestMapping("/api/v1/linked-accounts")
class LinkedAccountsController(
    private val accounts: LinkedAccounts,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: SignedInOwner,
    ): ResponseEntity<LinkedAccountList> =
        ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(LinkedAccountList(accounts.listMine(principal.ownerId).map { it.toItem() }))

    @DeleteMapping("/{linkedAccountId}")
    fun unlink(
        @AuthenticationPrincipal principal: SignedInOwner,
        @PathVariable linkedAccountId: String,
    ): ResponseEntity<UnlinkBody> {
        val id = runCatching { LinkedAccountId(UUID.fromString(linkedAccountId)) }.getOrNull()
        val result = accounts.unlink(principal.ownerId, id ?: throw LinkedAccountNotFound())
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(UnlinkBody(result.signOutConfirmed))
    }

    private fun LinkedAccountSummary.toItem() =
        LinkedAccountItem(
            id.value,
            displayName,
            PhoneBody(phone.countryCode, phone.lastDigits),
            state.wire,
            ChatSyncBody(chatsSynced, chatsTotal, chatSyncCompletedAt),
            createdAt,
        )
}
