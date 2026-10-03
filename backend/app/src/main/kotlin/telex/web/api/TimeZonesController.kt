package telex.web.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import telex.identity.TimeZones

data class TimeZoneList(
    val items: List<String>,
)

/** The known timezone list, whole and sorted; the same list the preference writes check against. */
@RestController
@RequestMapping("/api/v1/time-zones")
class TimeZonesController {
    @GetMapping
    fun list() = TimeZoneList(TimeZones.ids)
}
