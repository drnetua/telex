package telex.shared

import java.util.UUID

/** Contract for modules that report active Status Banner condition codes for an Owner. Interface only, no beans. */
fun interface StatusConditionSource {
    fun activeConditions(ownerId: UUID): Set<String>
}
