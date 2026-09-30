/**
 * Core: Owner, passkeys, magic link, quotas, BYOK keys, consent.
 */
@ApplicationModule(
    displayName = "Identity",
    allowedDependencies = {
        "messaging",
        "triage",
        "agents",
        "tools",
        "tasks",
        "scheduling",
        "audit",
        "telegram",
        "llm",
        "decision",
        "bot",
        "shared"
    }
)
package telex.identity;

import org.springframework.modulith.ApplicationModule;
