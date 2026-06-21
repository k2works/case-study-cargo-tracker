package cargotracker.routing.domain.model.repositories

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.valueobjects.VoyageNumber
import cargotracker.shared.domain.{CargoType, Location}

import java.time.Instant

/** 航海集約の永続化ポート。 */
trait VoyageRepository:
  def findByVoyageNumber(voyageNumber: VoyageNumber): Option[Voyage]
  def findAll(): Seq[Voyage]
  def save(voyage: Voyage): Unit

  /** US07: 検索条件に合致する航海を取得する（ADR 0006）。 すべての条件は省略可能。指定された条件のみで AND 絞り込みする。
    *
    * @param origin
    *   出発港（指定時は最初の区間の出発地が一致）
    * @param destination
    *   到着港（指定時は最後の区間の到着地が一致）
    * @param departureFrom
    *   出港時刻の下限（含む）
    * @param departureTo
    *   出港時刻の上限（含む）
    * @param cargoType
    *   貨物種別（指定時はその種別に対応した航海のみ）
    */
  def findByCriteria(
      origin: Option[Location] = None,
      destination: Option[Location] = None,
      departureFrom: Option[Instant] = None,
      departureTo: Option[Instant] = None,
      cargoType: Option[CargoType] = None
  ): Seq[Voyage]
