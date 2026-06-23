package cargotracker.tracking.domain.model.enums

/** 追跡例外イベントの種別（US19 / US20 / IT7）。
  *
  *   - `Delay`: 遅延
  *   - `Damage`: 破損
  *   - `Lost`: 紛失（escalation 必須）
  *   - `CustomsHold`: 通関保留
  */
enum ExceptionType:
  case Delay
  case Damage
  case Lost
  case CustomsHold

object ExceptionType:
  def fromName(name: String): Option[ExceptionType] = values.find(_.toString == name)
