package cargotracker.estimation.domain

import cargotracker.shared.domain.Money

/** 見積に紐づくルート候補。 */
final case class RouteCandidate(
    voyageNumber: String,
    transitPorts: List[String],
    transitDays: Int,
    estimatedCost: Money
):
  /** 表示用の経由港チェーン（例: 横浜→上海→Long Beach→New York） */
  def transitChain: String = transitPorts.mkString("→")
