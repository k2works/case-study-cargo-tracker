package cargotracker.handling.domain.model.repositories

import cargotracker.handling.domain.model.aggregates.HandlingActivity

trait HandlingActivityRepository:

  /** 自前で `DB.localTx` を開いて save する (既存呼出側互換)。 */
  def save(activity: HandlingActivity): Unit

  /** IT9 0.1 (ADR 0016 案 A): 外側 TX に参加して save する。
    *
    *   - HandlingOrchestrator が外側で `DB.localTx { implicit session => ... }` を開く場合に使う
    *   - ステップ間失敗時の rollback (Handling + Tracking + Booking 連動) を可能にする
    */
  def saveInTx(activity: HandlingActivity)(implicit session: scalikejdbc.DBSession): Unit

  def findByBookingId(bookingId: String): Seq[HandlingActivity]
  def findAll(): Seq[HandlingActivity]
