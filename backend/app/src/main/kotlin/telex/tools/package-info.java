/**
 * Core: tool registry, Scope checks in code.
 */
@ApplicationModule(
    displayName = "Tools",
    allowedDependencies = {
        "identity",
        "messaging",
        "triage",
        "agents",
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
package telex.tools;

import org.springframework.modulith.ApplicationModule;
