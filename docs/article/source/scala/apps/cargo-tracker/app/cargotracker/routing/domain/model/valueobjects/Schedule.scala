package cargotracker.routing.domain.model.valueobjects

/** 航海スケジュール。順序付き運送区間のリスト（domain-model.md 準拠）。
  *
  * 不変条件:
  *   - `carrierMovements` は非空
  *   - 連続性: 前区間の到着地 == 次区間の出発地
  *   - 時系列順: 前区間の到着時刻 ≤ 次区間の出発時刻
  */
final case class Schedule private (carrierMovements: List[CarrierMovement]):

  /** 全区間の出発時点リスト */
  def departures: List[CarrierMovement] = carrierMovements

  /** 全区間の到着時点リスト */
  def arrivals: List[CarrierMovement] = carrierMovements

  /** 始点（最初の区間の出発地）。`Schedule` は非空なので必ず存在する。 */
  def origin: cargotracker.shared.domain.Location = carrierMovements.head.departureLocation

  /** 終点（最後の区間の到着地） */
  def destination: cargotracker.shared.domain.Location =
    carrierMovements.last.arrivalLocation

object Schedule:

  sealed trait Error
  case object EmptyMovements extends Error
  case object DisconnectedMovements extends Error
  case object OutOfOrderTimes extends Error

  def apply(movements: List[CarrierMovement]): Either[Error, Schedule] =
    if movements.isEmpty then Left(EmptyMovements)
    else
      val invalid = movements.sliding(2).collectFirst {
        case prev :: next :: _ if prev.arrivalLocation != next.departureLocation =>
          DisconnectedMovements
        case prev :: next :: _ if prev.arrivalTime.isAfter(next.departureTime) =>
          OutOfOrderTimes
      }
      invalid.toLeft(new Schedule(movements))
