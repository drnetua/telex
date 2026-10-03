package telex.agents.internal.profile

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import telex.agents.ModelSlotKind
import telex.agents.SystemProfileKey
import telex.llm.ModelId

/** One system profile as configured: display name plus up to three ordered models per slot. */
data class SystemProfile(
    val key: SystemProfileKey,
    val displayName: String,
    val slots: Map<ModelSlotKind, List<ModelId>>,
    /** Models listed beyond the third in a slot, ignored but reported by the validator. */
    val ignored: Map<ModelSlotKind, List<ModelId>> = emptyMap(),
)

private const val MAX_CHAIN = 3

/** The three system profiles built from shipped defaults plus the Operator's overrides (ADR-0005). */
@Component
class SystemProfiles private constructor(
    private val profiles: List<SystemProfile>,
    /** Unknown profile keys or slots found in the settings (reported once at startup, then ignored). */
    val unknownEntries: List<String>,
) {
    @Autowired
    constructor(properties: SystemProfileProperties) : this(merge(properties.systemProfiles, emptyMap()))

    private constructor(built: SystemProfiles) : this(built.profiles, built.unknownEntries)

    fun all(): List<SystemProfile> = profiles

    fun profile(key: SystemProfileKey): SystemProfile = profiles.first { it.key == key }

    companion object {
        /** Maps are profile wire key -> slot wire name -> model ids. An override replaces that slot only. */
        fun merge(
            shipped: Map<String, Map<String, List<String>>>,
            overrides: Map<String, Map<String, List<String>>>,
        ): SystemProfiles {
            val unknown = mutableListOf<String>()
            val keys = SystemProfileKey.entries.associateBy { it.wire }
            val slots = ModelSlotKind.entries.associateBy { it.wire }
            val chains = SystemProfileKey.entries.associateWith { mutableMapOf<ModelSlotKind, List<String>>() }

            for (source in listOf(shipped, overrides)) {
                for ((keyName, bySlot) in source) {
                    val key = keys[keyName]
                    if (key == null) {
                        unknown += "profile '$keyName'"
                        continue
                    }
                    for ((slotName, ids) in bySlot) {
                        val slot = slots[slotName]
                        if (slot ==
                            null
                        ) {
                            unknown += "slot '$slotName' of profile '$keyName'"
                        } else {
                            chains.getValue(key)[slot] =
                                ids
                        }
                    }
                }
            }

            val built =
                SystemProfileKey.entries.map { key ->
                    val bySlot = chains.getValue(key)
                    SystemProfile(
                        key = key,
                        displayName = key.displayName,
                        slots =
                            ModelSlotKind.entries.associateWith { slot ->
                                bySlot[slot].orEmpty().take(MAX_CHAIN).map { ModelId(it) }
                            },
                        ignored =
                            ModelSlotKind.entries
                                .associateWith { slot -> bySlot[slot].orEmpty().drop(MAX_CHAIN).map { ModelId(it) } }
                                .filterValues { it.isNotEmpty() },
                    )
                }
            return SystemProfiles(built, unknown)
        }
    }
}
