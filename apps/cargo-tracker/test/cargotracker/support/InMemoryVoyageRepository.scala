package cargotracker.support

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.repositories.VoyageRepository
import cargotracker.routing.domain.model.valueobjects.VoyageNumber
import cargotracker.shared.domain.{CargoType, Location}

import java.time.Instant
import scala.collection.mutable

/** テスト用 [[VoyageRepository]] の InMemory 実装。
  *
  * IT4 タスク 0.3: 旧 RouteCandidateQueryServiceSpec の `findByCriteria` が常に 全件返す偽 stub になっていた問題を解消する。本実装は ScalikeJDBC 版と同等の
  * フィルタロジックを Scala コレクションで再現するため、QueryService の単体テストが 実 DB と整合した挙動を検証できる（契約テストパターン）。
  *
  *   - origin: 最初の区間の出発地と一致
  *   - destination: 最後の区間の到着地と一致
  *   - departureFrom / departureTo: 最初の区間の出港時刻が範囲内
  *   - cargoType: `supportedCargoTypes` に含む
  */
class InMemoryVoyageRepository extends VoyageRepository:

  val store: mutable.Buffer[Voyage] = mutable.Buffer.empty

  override def findByVoyageNumber(vn: VoyageNumber): Option[Voyage] =
    store.find(_.voyageNumber == vn)

  override def findAll(): Seq[Voyage] = store.toSeq

  override def save(v: Voyage): Unit =
    val idx = store.indexWhere(_.voyageNumber == v.voyageNumber)
    if idx >= 0 then store(idx) = v else store += v

  override def findByCriteria(
      origin: Option[Location],
      destination: Option[Location],
      departureFrom: Option[Instant],
      departureTo: Option[Instant],
      cargoType: Option[CargoType]
  ): Seq[Voyage] =
    store.toSeq.filter { v =>
      val firstDep = v.schedule.carrierMovements.head.departureTime
      origin.forall(_ == v.schedule.origin)
      && destination.forall(_ == v.schedule.destination)
      && departureFrom.forall(!firstDep.isBefore(_))
      && departureTo.forall(!firstDep.isAfter(_))
      && cargoType.forall(v.supportedCargoTypes.contains)
    }
