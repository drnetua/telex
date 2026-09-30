/**
 * Core: User Task, Approval (TTL), undo window, reminders.
 */
@ApplicationModule(
    displayName = "Tasks",
    allowedDependencies = {
        "identity",
        "messaging",
        "triage",
        "agents",
        "tools",
        "scheduling",
        "audit",
        "telegram",
        "llm",
        "decision",
        "bot",
        "shared"
    }
)
package telex.tasks;

import org.springframework.modulith.ApplicationModule;
