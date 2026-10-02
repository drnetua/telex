/**
 * Core: System 1 local prefilter + Jev decision per event.
 */
@ApplicationModule(
    displayName = "Triage",
    allowedDependencies = {
        "identity",
        "messaging",
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
package telex.triage;

import org.springframework.modulith.ApplicationModule;
