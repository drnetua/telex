/**
 * Interface: REST controllers, SSE stream, SPA hosting, RFC 9457 errors.
 */
@ApplicationModule(
    displayName = "Web",
    allowedDependencies = {
        "identity",
        "messaging",
        "triage",
        "agents",
        "tools",
        "tasks",
        "scheduling",
        "audit",
        "shared"
    }
)
package telex.web;

import org.springframework.modulith.ApplicationModule;
