package cargotracker.handling.application.commandservices

import cargotracker.handling.domain.model.aggregates.HandlingActivity
import cargotracker.handling.domain.model.ports.{BookingNotificationPort, HandlingCargoQueryPort, TrackingLookupPort}
import cargotracker.handling.domain.model.repositories.HandlingActivityRepository
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable

class HandlingOrchestratorSpec extends AnyFunSuite with Matchers with EitherValues:

  private class InMemoryHandlingRepo extends HandlingActivityRepository:
    val store: mutable.Buffer[HandlingActivity] = mutable.Buffer.empty
    override def save(a: HandlingActivity): Unit = store += a
    override def findAll(): Seq[HandlingActivity] = store.toSeq
    override def findByBookingId(bookingId: String): Seq[HandlingActivity] =
      store.toSeq.filter(_.bookingId == bookingId)

  private class FakeTrackingPort(bookingFor: Map[String, String]) extends TrackingLookupPort:
    val recordedEvent: AtomicReference[Option[String]] = AtomicReference(None)
    val recordedRouteDeviation: AtomicReference[Option[Boolean]] = AtomicReference(None)
    override def findBookingIdByTrackingNumber(tn: String): Option[String] = bookingFor.get(tn)
    override def recordEvent(
        trackingNumber: String,
        eventType: String,
        eventTime: Instant,
        locationUnLocode: String,
        voyageNumber: Option[String],
        routeDeviation: Boolean
    ): Either[String, Unit] =
      recordedEvent.set(Some(eventType))
      recordedRouteDeviation.set(Some(routeDeviation))
      Right(())

  private class FakeBookingPort extends BookingNotificationPort:
    val logged: AtomicReference[Option[String]] = AtomicReference(None)
    val delivered: AtomicReference[Option[String]] = AtomicReference(None)
    override def logHandling(
        bookingId: String,
        trackingNumber: String,
        eventType: String,
        locationUnLocode: String
    ): Either[String, Unit] =
      logged.set(Some(eventType))
      Right(())
    override def completeDelivery(
        bookingId: String,
        trackingNumber: String,
        locationUnLocode: String,
        recipientConfirmation: String
    ): Either[String, Unit] =
      delivered.set(Some(recipientConfirmation))
      Right(())

  /** Fake HandlingCargoQueryPort (IT8 0.11 / T3): 経路上 UN/LOCODE 集合を bookingId 別に指定。 None なら itinerary 未紐付け扱い。 */
  private class FakeCargoQueryPort(itineraryByBooking: Map[String, Set[String]]) extends HandlingCargoQueryPort:
    override def isOnRoute(bookingId: String, unLocode: String): Boolean =
      itineraryByBooking.get(bookingId).exists(_.contains(unLocode))
    override def findItineraryLocations(bookingId: String): Option[Set[String]] =
      itineraryByBooking.get(bookingId)

  private val now = Instant.parse("2026-09-20T10:00:00Z")

  private def orchestratorWith(
      tracking: FakeTrackingPort,
      booking: FakeBookingPort,
      cargoQuery: FakeCargoQueryPort,
      handlingRepo: InMemoryHandlingRepo = new InMemoryHandlingRepo
  ): (HandlingOrchestrator, InMemoryHandlingRepo) =
    val orchestrator = new HandlingOrchestrator(
      new HandlingCommandService(handlingRepo),
      tracking,
      booking,
      cargoQuery
    )
    (orchestrator, handlingRepo)

  test("register: 不明な追跡番号は Left（HandlingActivity 永続化は行わない）"):
    val (orchestrator, repo) = orchestratorWith(
      new FakeTrackingPort(Map.empty),
      new FakeBookingPort,
      new FakeCargoQueryPort(Map.empty)
    )
    val input = RegisterHandlingFlowInput(
      trackingNumber = "TN-UNKNOWN",
      eventType = "Receive",
      completionTime = now,
      locationUnLocode = "JPTYO"
    )
    orchestrator.register(input).isLeft shouldBe true
    repo.store shouldBe empty

  test("register: Receive イベントで Handling 保存 + Tracking 追記 + Booking 通知が全実行 (IT7 0.3)"):
    val tracking = new FakeTrackingPort(Map("TN-000001" -> "BK-000001"))
    val booking = new FakeBookingPort
    val (orchestrator, repo) = orchestratorWith(
      tracking,
      booking,
      new FakeCargoQueryPort(Map("BK-000001" -> Set("JPTYO", "USNYC")))
    )
    val input = RegisterHandlingFlowInput(
      trackingNumber = "TN-000001",
      eventType = "Receive",
      completionTime = now,
      locationUnLocode = "JPTYO",
      operatorName = Some("田中")
    )
    orchestrator.register(input).value shouldBe ()
    repo.store should have size 1
    tracking.recordedEvent.get() shouldBe Some("Receive")
    booking.logged.get() shouldBe Some("Receive")
    booking.delivered.get() shouldBe None

  test("register: Claim イベントは completeDelivery まで実行する (IT7 0.3 / US16 統合)"):
    val tracking = new FakeTrackingPort(Map("TN-CLAIM1" -> "BK-CLAIM1"))
    val booking = new FakeBookingPort
    val (orchestrator, _) = orchestratorWith(
      tracking,
      booking,
      new FakeCargoQueryPort(Map("BK-CLAIM1" -> Set("JPTYO", "USNYC")))
    )
    val input = RegisterHandlingFlowInput(
      trackingNumber = "TN-CLAIM1",
      eventType = "Claim",
      completionTime = now,
      locationUnLocode = "USNYC",
      recipientConfirmation = Some("署名: 山田"),
      recipientConfirmationType = Some("Signature")
    )
    orchestrator.register(input).value shouldBe ()
    booking.delivered.get() shouldBe Some("署名: 山田")

  // IT8 0.11 (T3): routeDeviation 自動判定の同値クラステスト 3 件

  test("register: 経路上の予定地で発生した荷役は routeDeviation=false (IT8 0.11 / T3)"):
    val tracking = new FakeTrackingPort(Map("TN-DEV1" -> "BK-DEV1"))
    val booking = new FakeBookingPort
    val (orchestrator, repo) = orchestratorWith(
      tracking,
      booking,
      new FakeCargoQueryPort(Map("BK-DEV1" -> Set("JPTYO", "SGSIN", "USNYC")))
    )
    val input = RegisterHandlingFlowInput(
      trackingNumber = "TN-DEV1",
      eventType = "Load",
      completionTime = now,
      locationUnLocode = "SGSIN",
      voyageNumber = Some("V-100")
    )
    orchestrator.register(input).value shouldBe ()
    tracking.recordedRouteDeviation.get() shouldBe Some(false)
    repo.store.head.routeDeviation shouldBe false

  test("register: 経路外の場所で発生した荷役は routeDeviation=true (IT8 0.11 / T3)"):
    val tracking = new FakeTrackingPort(Map("TN-DEV2" -> "BK-DEV2"))
    val booking = new FakeBookingPort
    val (orchestrator, repo) = orchestratorWith(
      tracking,
      booking,
      new FakeCargoQueryPort(Map("BK-DEV2" -> Set("JPTYO", "USNYC")))
    )
    val input = RegisterHandlingFlowInput(
      trackingNumber = "TN-DEV2",
      eventType = "Unload",
      completionTime = now,
      locationUnLocode = "DEHAM", // ← Itinerary に含まれない
      voyageNumber = Some("V-200")
    )
    orchestrator.register(input).value shouldBe ()
    tracking.recordedRouteDeviation.get() shouldBe Some(true)
    repo.store.head.routeDeviation shouldBe true

  test("register: Itinerary 未紐付けの貨物は routeDeviation=true (経路逸脱扱い / IT8 0.11 / T3)"):
    val tracking = new FakeTrackingPort(Map("TN-DEV3" -> "BK-DEV3"))
    val booking = new FakeBookingPort
    val (orchestrator, repo) = orchestratorWith(
      tracking,
      booking,
      new FakeCargoQueryPort(Map.empty) // BK-DEV3 の itinerary 情報なし
    )
    val input = RegisterHandlingFlowInput(
      trackingNumber = "TN-DEV3",
      eventType = "Receive",
      completionTime = now,
      locationUnLocode = "JPTYO"
    )
    orchestrator.register(input).value shouldBe ()
    tracking.recordedRouteDeviation.get() shouldBe Some(true)
    repo.store.head.routeDeviation shouldBe true
