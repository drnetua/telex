/**
 * Integration (ACL): Jev decisions, guardrails, judge.
 */
@ApplicationModule(
    displayName = "Decision",
    allowedDependencies = {
        "shared"
    }
)
package telex.decision;

import org.springframework.modulith.ApplicationModule;
