package cargotracker.routing.domain.model.aggregates

import cargotracker.routing.domain.model.valueobjects.{Schedule, VoyageNumber}

/** 航海（Routing Context の集約ルート、domain-model.md 準拠）。
  *
  *   - 業務キー: `VoyageNumber`
  *   - スケジュールは `Schedule` 値オブジェクトでカプセル化
  *   - `version` は楽観ロック用（IT2 タスク 0.11 / data-model.md）
  */
final case class Voyage private (
    voyageNumber: VoyageNumber,
    schedule: Schedule,
    version: Int
):

  /** スケジュール更新（US25 / `UpdateVoyageCommand`）。 voyageNumber は不変、version は IT3 で集約に活性化する。 */
  def updateSchedule(newSchedule: Schedule): Voyage =
    copy(schedule = newSchedule)

object Voyage:

  /** 新規航海を生成する（US24 / `RegisterVoyageCommand`）。 */
  def register(voyageNumber: VoyageNumber, schedule: Schedule): Voyage =
    new Voyage(voyageNumber, schedule, version = 0)

  /** 永続化からの復元 */
  def reconstruct(
      voyageNumber: VoyageNumber,
      schedule: Schedule,
      version: Int
  ): Voyage =
    new Voyage(voyageNumber, schedule, version)
