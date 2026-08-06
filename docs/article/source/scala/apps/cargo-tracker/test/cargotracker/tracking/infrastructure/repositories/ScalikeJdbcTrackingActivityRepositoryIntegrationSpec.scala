package cargotracker.tracking.infrastructure.repositories

import cargotracker.shared.domain.OptimisticLockException
import cargotracker.support.{DbCleanupSupport, PostgresContainerSupport}
import cargotracker.tracking.domain.model.aggregates.TrackingActivity
import cargotracker.tracking.domain.model.entities.TrackingActivityEvent
import cargotracker.tracking.domain.model.valueobjects.TrackingLocation
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

/** TrackingActivity 集約の楽観ロック競合検証（IT6 タスク 0.5 / H5 解消）。 */
class ScalikeJdbcTrackingActivityRepositoryIntegrationSpec
    extends AnyFunSuite
    with Matchers
    with PostgresContainerSupport
    with DbCleanupSupport:

  private def evt(eventType: String, sec: Long): TrackingActivityEvent =
    TrackingActivityEvent(
      eventType = eventType,
      eventTime = Instant.ofEpochSecond(sec),
      location = TrackingLocation.of("JNTKO"),
      voyageNumber = None,
      routeDeviation = false
    )

  test("appendEvent: 正常系 - version+1 された TrackingActivity を返却する"):
    val repo = new ScalikeJdbcTrackingActivityRepository
    val tn = repo.nextTrackingNumber()
    val Right(activity) = TrackingActivity.issue(tn, "BK-LOCK01"): @unchecked
    repo.save(activity)

    val loaded = repo.findByTrackingNumber(tn).get
    val event = evt("Receive", 100)
    val Right(withEvent) = loaded.addEvent(event): @unchecked
    val returned = repo.appendEvent(withEvent, event)
    returned.version shouldBe loaded.version + 1

  test("appendEvent: 競合 - 古い version の集約で再 appendEvent すると OptimisticLockException"):
    val repo = new ScalikeJdbcTrackingActivityRepository
    val tn = repo.nextTrackingNumber()
    val Right(activity) = TrackingActivity.issue(tn, "BK-LOCK02"): @unchecked
    repo.save(activity)

    val loaded = repo.findByTrackingNumber(tn).get
    // セッション A が先に append（DB 側 version=1 へ）
    val eventA = evt("Receive", 100)
    val Right(withA) = loaded.addEvent(eventA): @unchecked
    repo.appendEvent(withA, eventA)

    // セッション B が同じ古い version の集約で append しようとすると競合
    val eventB = evt("Load", 200)
    val Right(withB) = loaded.addEvent(eventB): @unchecked // loaded は依然 version=0
    val ex = intercept[OptimisticLockException] {
      repo.appendEvent(withB, eventB)
    }
    ex.entityType shouldBe "TrackingActivity"
    ex.identifier shouldBe tn.value
