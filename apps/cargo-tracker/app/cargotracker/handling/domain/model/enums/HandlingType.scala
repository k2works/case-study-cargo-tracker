package cargotracker.handling.domain.model.enums

/** 荷役種別（domain-model.md L871）。
  *
  *   - 5 値: Receive / Load / Unload / Customs / Claim
  *   - `Load` / `Unload` は航海番号必須、それ以外は不要
  *   - IT5 では `Receive` / `Load` / `Unload` のみ UI に開放（`Customs` / `Claim` は IT7 / IT6 で開放）
  */
enum HandlingType:
  case Receive, Load, Unload, Customs, Claim

  /** 当該荷役種別が `voyageNumber` を必須とするか。 */
  def requiresVoyage: Boolean = this match
    case Load | Unload => true
    case _ => false

object HandlingType:
  def fromName(name: String): Option[HandlingType] = values.find(_.toString == name)
