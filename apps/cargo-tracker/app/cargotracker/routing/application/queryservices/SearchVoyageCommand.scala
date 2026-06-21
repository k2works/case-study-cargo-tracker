package cargotracker.routing.application.queryservices

import java.time.LocalDate

/** 航海検索条件（US07）。すべて任意項目。 */
final case class SearchVoyageCommand(
    origin: Option[String] = None,
    destination: Option[String] = None,
    departureDateFrom: Option[LocalDate] = None,
    departureDateTo: Option[LocalDate] = None,
    cargoType: Option[String] = None
)
