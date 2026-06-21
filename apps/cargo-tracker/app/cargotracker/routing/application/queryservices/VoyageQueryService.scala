package cargotracker.routing.application.queryservices

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.repositories.VoyageRepository
import cargotracker.routing.domain.model.valueobjects.VoyageNumber
import cargotracker.shared.domain.{CargoType, Location}

import java.time.{LocalDate, ZoneId}
import javax.inject.{Inject, Singleton}

/** 航海検索条件（US07）。すべて任意項目。 */
final case class SearchVoyageCommand(
    origin: Option[String] = None,
    destination: Option[String] = None,
    departureDateFrom: Option[LocalDate] = None,
    departureDateTo: Option[LocalDate] = None,
    cargoType: Option[String] = None
)

/** 航海の参照系（CQRS Query 側、IT3 US07 検索）。 */
@Singleton
class VoyageQueryService @Inject() (repository: VoyageRepository):

  def findAll(): Seq[Voyage] = repository.findAll()

  def findByVoyageNumber(voyageNumber: String): Option[Voyage] =
    VoyageNumber(voyageNumber).toOption.flatMap(repository.findByVoyageNumber)

  /** US07: 検索条件に合致する航海を取得する。 不正な入力（UnLocode / CargoType 名称）は条件を無視せず空 Seq を返し、上位層でフィードバックする。
    */
  def search(command: SearchVoyageCommand): Either[String, Seq[Voyage]] =
    for
      origin <- liftLocation(command.origin, "出発港")
      destination <- liftLocation(command.destination, "到着港")
      cargoType <- liftCargoType(command.cargoType)
    yield
      val departureFrom = command.departureDateFrom.map(_.atStartOfDay(ZoneId.systemDefault).toInstant)
      val departureTo = command.departureDateTo.map(
        _.plusDays(1).atStartOfDay(ZoneId.systemDefault).toInstant.minusNanos(1_000_000)
      )
      repository.findByCriteria(
        origin = origin,
        destination = destination,
        departureFrom = departureFrom,
        departureTo = departureTo,
        cargoType = cargoType
      )

  private def liftLocation(raw: Option[String], label: String): Either[String, Option[Location]] =
    raw.filter(_.nonEmpty) match
      case None => Right(None)
      case Some(str) => Location(str).left.map(_ => s"${label}の UnLocode 形式が不正です").map(Some.apply)

  private def liftCargoType(raw: Option[String]): Either[String, Option[CargoType]] =
    raw.filter(_.nonEmpty) match
      case None => Right(None)
      case Some(name) =>
        CargoType.fromName(name).toRight(s"貨物種別が不正です: $name").map(Some.apply)
