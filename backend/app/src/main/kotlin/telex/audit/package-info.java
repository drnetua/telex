/**
 * Core: append-only log of agent and Owner actions.
 */
@ApplicationModule(
    displayName = "Audit",
    allowedDependencies = {
        "identity",
        "messaging",
        "triage",
        "agents",
        "tools",
        "tasks",
        "scheduling",
        "telegram",
        "llm",
        "decision",
        "bot",
        "shared"
    }
)
package telex.audit;

import org.springframework.modulith.ApplicationModule;
