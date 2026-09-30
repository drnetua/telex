/**
 * Shared kernel (not a domain module): typed ids, problem codes, value types. No Spring beans.
 */
@ApplicationModule(
    displayName = "Shared kernel",
    type = ApplicationModule.Type.OPEN,
    allowedDependencies = {}
)
package telex.shared;

import org.springframework.modulith.ApplicationModule;
