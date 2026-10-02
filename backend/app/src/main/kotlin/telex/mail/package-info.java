/**
 * Integration (ACL): delivers outgoing email over SMTP.
 */
@ApplicationModule(
    displayName = "Mail",
    allowedDependencies = {
        "shared"
    }
)
package telex.mail;

import org.springframework.modulith.ApplicationModule;
