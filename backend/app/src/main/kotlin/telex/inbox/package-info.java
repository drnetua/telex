/**
 * Core: the Inbox contract and the sum of waiting items over producer modules.
 */
@ApplicationModule(
    displayName = "Inbox",
    allowedDependencies = {
        "shared"
    }
)
package telex.inbox;

import org.springframework.modulith.ApplicationModule;
