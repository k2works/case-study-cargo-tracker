package cargotracker.routing.domain.model.valueobjects

/** 経路選択の状態（US09）。
  *
  *   - `Pending`: 経路候補画面で表示されているが確定操作前
  *   - `Confirmed`: 営業担当者が「この経路で確定」を押下し予約に紐付け済み
  */
enum RouteSelectionStatus:
  case Pending, Confirmed

object RouteSelectionStatus:
  def fromName(name: String): Option[RouteSelectionStatus] =
    values.find(_.toString == name)
