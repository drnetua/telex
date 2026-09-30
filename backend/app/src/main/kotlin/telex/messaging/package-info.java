/**
 * Core: Channel, Channel Set, message history, pgvector search.
 */
@ApplicationModule(
    displayName = "Messaging",
    allowedDependencies = {
        "identity",
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
package telex.messaging;

import org.springframework.modulith.ApplicationModule;
