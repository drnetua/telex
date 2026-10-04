package telex.agents.internal.profile

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `telex.models.system-profiles.<fast|balanced|careful>.<text|vision|image>`, a list of model ids.
 * The shipped defaults live in application.yaml; an Operator override replaces a whole slot list.
 */
@ConfigurationProperties("telex.models")
data class SystemProfileProperties(
    val systemProfiles: Map<String, Map<String, List<String>>> = emptyMap(),
)
