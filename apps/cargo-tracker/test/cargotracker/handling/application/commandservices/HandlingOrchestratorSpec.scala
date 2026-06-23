package cargotracker.handling.application.commandservices

import cargotracker.handling.domain.model.aggregates.HandlingActivity
import cargotracker.handling.domain.model.ports.{BookingNotificationPort, TrackingLookupPort}
import cargotracker.handling.domain.model.repositories.HandlingActivityRepository
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable

class HandlingOrchestratorSpec extends AnyFunSuite with Matchers:

  private class InMemoryHandlingRepo extends HandlingActivityRepository:
    val store: mutable.Buffer[HandlingActivity] = mutable.Buffer.empty
    override def save(a: HandlingActivity): Unit = store += a
    override def findAll(): Seq[HandlingActivity] = store.toSeq
    override def findByBookingId(bookingId: String): Seq[HandlingActivity] =
      store.toSeq.filter(_.bookingId == bookingId)

  private class FakeTrackingPort(bookingFor: Map[String, String]) extends TrackingLookupPort:
    val recorded: AtomicReference[Option[String]] = AtomicReference(None)
    override def findBookingIdByTrackingNumber(tn: String): Option[String] = bookingFor.get(tn)
    override def recordEvent(
        trackingNumber: String,
        eventType: String,
        eventTime: Instant,
        locationUnLocode: String,
        voyageNumber: Option[String],
        routeDeviation: Boolean
    ): Either[String, Unit] =
      recorded.set(Some(eventType))
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

  private val now = Instant.parse("2026-09-20T10:00:00Z")

  test("register: 不明な追跡番号は Left（HandlingActivity 永続化は行わない）"):
    val handlingRepo = new InMemoryHandlingRepo
    val orchestrator = new HandlingOrchestrator(
      new HandlingCommandService(handlingRepo),
      new FakeTrackingPort(Map.empty),
      new FakeBookingPort
    )
    val input = RegisterHandlingFlowInput(
      trackingNumber = "TN-UNKNOWN",
      eventType = "Receive",
      completionTime = now,
      locationUnLocode = "JPTYO"
    )
    val result = orchestrator.register(input)
    result.isLeft shouldBe true
    handlingRepo.store shouldBe empty

  test("register: Receive イベントで Handling 保存 + Tracking 追記 + Booking 通知が全実行 (IT7 0.3)"):
    val handlingRepo = new InMemoryHandlingRepo
    val tracking = new FakeTrackingPort(Map("TN-000001" -> "BK-000001"))
    val booking = new FakeBookingPort
    val orchestrator = new HandlingOrchestrator(
      new HandlingCommandService(handlingRepo),
      tracking,
      booking
    )
    val input = RegisterHandlingFlowInput(
      trackingNumber = "TN-000001",
      eventType = "Receive",
      completionTime = now,
      locationUnLocode = "JPTYO",
      operatorName = Some("田中")
    )
    val Right(_) = orchestrator.register(input): @unchecked
    handlingRepo.store should have size 1
    tracking.recorded.get() shouldBe Some("Receive")
    booking.logged.get() shouldBe Some("Receive")
    booking.delivered.get() shouldBe None // Claim ではないので completeDelivery 未発火

  test("register: Claim イベントは completeDelivery まで実行する (IT7 0.3 / US16 統合)"):
    val handlingRepo = new InMemoryHandlingRepo
    val tracking = new FakeTrackingPort(Map("TN-CLAIM1" -> "BK-CLAIM1"))
    val booking = new FakeBookingPort
    val orchestrator = new HandlingOrchestrator(
      new HandlingCommandService(handlingRepo),
      tracking,
      booking
    )
    val input = RegisterHandlingFlowInput(
      trackingNumber = "TN-CLAIM1",
      eventType = "Claim",
      completionTime = now,
      locationUnLocode = "USNYC",
      recipientConfirmation = Some("署名: 山田"),
      recipientConfirmationType = Some("Signature")
    )
    val Right(_) = orchestrator.register(input): @unchecked
    booking.delivered.get() shouldBe Some("署名: 山田")
