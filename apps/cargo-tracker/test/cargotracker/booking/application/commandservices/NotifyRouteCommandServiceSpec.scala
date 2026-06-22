package cargotracker.booking.application.commandservices

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.aggregates.{Cargo, NotificationLog}
import cargotracker.booking.domain.model.repositories.{CargoRepository, NotificationLogRepository}
import cargotracker.booking.domain.model.valueobjects.{
  BookingId,
  BookingStatus,
  CargoSpec,
  Itinerary,
  NotificationType,
  RouteSpecification
}
import cargotracker.shared.domain.{CargoType, Location, ShipperId, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

import java.time.{Clock, Instant, LocalDate, ZoneOffset}
import scala.collection.mutable

class NotifyRouteCommandServiceSpec extends AnyFunSuite with Matchers:

  private val bookingId = BookingId.unsafeFrom("BK-000001")
  private val shipperId = ShipperId.unsafeFrom("SH-000001")
  private val routeSpec = RouteSpecification(
    Location.unsafeFrom("JPTYO"),
    Location.unsafeFrom("USLAX"),
    LocalDate.parse("2099-12-31")
  ).toOption.get
  private val spec = CargoSpec(
    cargoType = CargoType.General,
    weight = Weight.unsafeFrom(1000),
    description = None,
    quantity = None,
    hazardous = None
  )
  private val fixedClock = Clock.fixed(Instant.parse("2026-06-21T10:00:00Z"), ZoneOffset.UTC)

  private class InMemoryCargoRepo extends CargoRepository:
    val store: mutable.Map[String, Cargo] = mutable.Map.empty
    override def findById(id: BookingId): Option[Cargo] = store.get(id.value)
    override def findAll(): Seq[Cargo] = store.values.toSeq
    override def findByStatus(s: BookingStatus): Seq[Cargo] = store.values.filter(_.status == s).toSeq
    override def save(c: Cargo): Unit = store(c.bookingId.value) = c
    override def nextIdentity(): BookingId = BookingId.unsafeFrom("BK-999999")

  private class InMemoryNotificationRepo extends NotificationLogRepository:
    val store: mutable.Buffer[NotificationLog] = mutable.Buffer.empty
    override def findByBookingId(id: BookingId): Seq[NotificationLog] =
      store.toSeq.filter(_.bookingId == id)
    override def save(log: NotificationLog): Unit = store += log

  private def routeAssignedCargo: Cargo =
    Cargo.reconstruct(
      bookingId = bookingId,
      shipperId = shipperId,
      routeSpecification = routeSpec,
      cargoSpec = spec,
      status = BookingStatus.RouteAssigned,
      version = 0,
      itinerary = Some(Itinerary.unsafeFrom(List("VY-1", "VY-2")))
    )

  test("notify: RouteAssigned 予約に通知を発行する"):
    val cargoRepo = new InMemoryCargoRepo
    cargoRepo.save(routeAssignedCargo)
    val notifRepo = new InMemoryNotificationRepo
    val svc = new NotifyRouteCommandService(cargoRepo, notifRepo, fixedClock)

    val Right(log) = svc.notify(bookingId.value): @unchecked
    log.notificationType shouldBe NotificationType.RouteNotified
    log.sentAt shouldBe Instant.parse("2026-06-21T10:00:00Z")
    // IT5 H5: payload を JSON 構造として検証する（部分文字列マッチではない）
    val json = Json.parse(log.payload)
    (json \ "bookingId").as[String] shouldBe bookingId.value
    (json \ "origin").as[String] shouldBe "JPTYO"
    (json \ "destination").as[String] shouldBe "USLAX"
    (json \ "arrivalDeadline").as[String] shouldBe "2099-12-31"
    (json \ "voyages").as[List[String]] shouldBe List("VY-1", "VY-2")
    notifRepo.store should have size 1

  test("notify: voyages が単一でも JSON 配列として正しく serialise される（H5 構造アサート）"):
    val singleLeg = Cargo.reconstruct(
      bookingId = bookingId,
      shipperId = shipperId,
      routeSpecification = routeSpec,
      cargoSpec = spec,
      status = BookingStatus.RouteAssigned,
      version = 0,
      itinerary = Some(Itinerary.unsafeFrom(List("VY-ONLY")))
    )
    val cargoRepo = new InMemoryCargoRepo
    cargoRepo.save(singleLeg)
    val svc = new NotifyRouteCommandService(cargoRepo, new InMemoryNotificationRepo, fixedClock)

    val Right(log) = svc.notify(bookingId.value): @unchecked
    val json = Json.parse(log.payload)
    (json \ "voyages").as[List[String]] shouldBe List("VY-ONLY")

  test("notify: 経路未紐付け（Preliminary）は拒否"):
    val pre = Cargo.reconstruct(bookingId, shipperId, routeSpec, spec, BookingStatus.Preliminary, 0, None)
    val cargoRepo = new InMemoryCargoRepo
    cargoRepo.save(pre)
    val svc = new NotifyRouteCommandService(cargoRepo, new InMemoryNotificationRepo, fixedClock)
    val Left(msg) = svc.notify(bookingId.value): @unchecked
    msg should include("経路未紐付け")

  test("notify: 存在しない予約は Left"):
    val svc = new NotifyRouteCommandService(new InMemoryCargoRepo, new InMemoryNotificationRepo, fixedClock)
    svc.notify("BK-999000") shouldBe Left("予約 BK-999000 が見つかりません")

  test("notify: 不正な予約 ID 形式は Left"):
    val svc = new NotifyRouteCommandService(new InMemoryCargoRepo, new InMemoryNotificationRepo, fixedClock)
    svc.notify("invalid").left.toOption.get should include("形式が不正")
