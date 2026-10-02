/**
 * Core: per-Owner cron on Quartz, ScheduleFired.
 */
@ApplicationModule(
    displayName = "Scheduling",
    allowedDependencies = {
        "identity",
        "messaging",
        "triage",
        "agents",
        "tools",
        "tasks",
        "audit",
        "telegram",
        "llm",
        "decision",
        "bot",
        "shared"
    }
)
package telex.scheduling;

import org.springframework.modulith.ApplicationModule;
