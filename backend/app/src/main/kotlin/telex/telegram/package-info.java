/**
 * Integration (ACL): Linked Account login, read/send, events from TDLib.
 */
@ApplicationModule(
    displayName = "Telegram",
    allowedDependencies = {
        "shared"
    }
)
package telex.telegram;

import org.springframework.modulith.ApplicationModule;
