package cargotracker.routing.domain.model.aggregates

import cargotracker.routing.domain.model.valueobjects.{Schedule, VoyageNumber}
import cargotracker.shared.domain.CargoType

/** 航海（Routing Context の集約ルート、domain-model.md / ADR 0006 準拠）。
  *
  *   - 業務キー: `VoyageNumber`
  *   - スケジュールは `Schedule` 値オブジェクトでカプセル化
  *   - `vesselName`・`carrierCode` は US07 検索条件（ADR 0006）
  *   - `supportedCargoTypes` は US08 経路候補算出時の貨物種別フィルタ（多対多）
  *   - `version` は楽観ロック用
  */
final case class Voyage private (
    voyageNumber: VoyageNumber,
    schedule: Schedule,
    vesselName: String,
    carrierCode: String,
    supportedCargoTypes: Set[CargoType],
    version: Int
):

  def updateSchedule(newSchedule: Schedule): Voyage =
    copy(schedule = newSchedule)

  def updateVessel(newVesselName: String, newCarrierCode: String): Voyage =
    copy(vesselName = newVesselName, carrierCode = newCarrierCode)

  def updateSupportedCargoTypes(types: Set[CargoType]): Voyage =
    copy(supportedCargoTypes = types)

  def supports(cargoType: CargoType): Boolean =
    supportedCargoTypes.contains(cargoType)

object Voyage:

  def register(voyageNumber: VoyageNumber, schedule: Schedule): Voyage =
    new Voyage(voyageNumber, schedule, "", "", Set.empty, version = 0)

  def register(
      voyageNumber: VoyageNumber,
      schedule: Schedule,
      vesselName: String,
      carrierCode: String,
      supportedCargoTypes: Set[CargoType]
  ): Voyage =
    new Voyage(voyageNumber, schedule, vesselName, carrierCode, supportedCargoTypes, version = 0)

  def reconstruct(
      voyageNumber: VoyageNumber,
      schedule: Schedule,
      version: Int
  ): Voyage =
    new Voyage(voyageNumber, schedule, "", "", Set.empty, version)

  def reconstruct(
      voyageNumber: VoyageNumber,
      schedule: Schedule,
      vesselName: String,
      carrierCode: String,
      supportedCargoTypes: Set[CargoType],
      version: Int
  ): Voyage =
    new Voyage(voyageNumber, schedule, vesselName, carrierCode, supportedCargoTypes, version)
