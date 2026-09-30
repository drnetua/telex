/**
 * Core: Agent, Run, templates, ChatClient, memory.
 */
@ApplicationModule(
    displayName = "Agents",
    allowedDependencies = {
        "identity",
        "messaging",
        "triage",
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
package telex.agents;

import org.springframework.modulith.ApplicationModule;
