package cargotracker.booking.application.commandservices

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus}
import cargotracker.shared.domain.ShipperId
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable

class BookingCommandServiceSpec extends AnyFunSuite with Matchers:

  private class InMemoryNotificationLogRepository
      extends cargotracker.booking.domain.model.repositories.NotificationLogRepository:
    val store: mutable.Buffer[cargotracker.booking.domain.model.aggregates.NotificationLog] = mutable.Buffer.empty
    override def findByBookingId(id: BookingId) = store.toSeq.filter(_.bookingId == id)
    override def save(log: cargotracker.booking.domain.model.aggregates.NotificationLog): Unit = store += log

  private class InMemoryCargoRepository extends CargoRepository:
    private val store: mutable.Map[BookingId, Cargo] = mutable.Map.empty
    private val seq: AtomicInteger = AtomicInteger(0)
    override def findById(id: BookingId): Option[Cargo] = store.get(id)
    override def findAll(): Seq[Cargo] = store.values.toSeq
    override def findByStatus(
        status: cargotracker.booking.domain.model.valueobjects.BookingStatus
    ): Seq[Cargo] = store.values.filter(_.status == status).toSeq
    override def save(c: Cargo): Unit = store.update(c.bookingId, c)
    override def nextIdentity(): BookingId =
      BookingId.unsafeFrom(f"BK-${seq.incrementAndGet()}%06d")
    def saved: Seq[Cargo] = store.values.toSeq

  private val acceptingChecker: ShipperExistenceChecker = (_: ShipperId) => true
  private val rejectingChecker: ShipperExistenceChecker = (_: ShipperId) => false

  private def baseCommand: BookCargoCommand = BookCargoCommand(
    shipperCode = "SH-000001",
    origin = "JPYOK",
    destination = "USNYC",
    arrivalDeadline = LocalDate.now.plusDays(30),
    cargoType = "General",
    weightKg = 800L,
    description = Some("テスト貨物"),
    quantity = Some(1),
    hazardousClass = None,
    hazardousUnNumber = None,
    hazardousProperName = None
  )

  test("有効な入力で予約を生成し Preliminary 状態で保存する"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )

    val Right(cargo) = service.book(baseCommand): @unchecked

    cargo.status shouldBe BookingStatus.Preliminary
    cargo.bookingId.value shouldBe "BK-000001"
    repo.saved should have size 1

  test("出発地と目的地が同じならエラー"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Left(msg) = service.book(baseCommand.copy(destination = baseCommand.origin)): @unchecked
    msg should include("出発地と目的地が同じ")

  test("不正な UnLocode はエラー"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    service.book(baseCommand.copy(origin = "xx")) shouldBe Left("出発地の UnLocode 形式が不正です")

  test("存在しない荷主は ShipperExistenceChecker で弾かれ永続化されない"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      rejectingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )

    val Left(msg) = service.book(baseCommand): @unchecked
    msg should include("荷主 SH-000001 が見つかりません")
    repo.saved shouldBe empty

  test("危険物 3 フィールド全揃いで HazardousDeclaration が反映される"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service
      .book(
        baseCommand.copy(
          cargoType = "Hazardous",
          hazardousClass = Some("3"),
          hazardousUnNumber = Some("UN1170"),
          hazardousProperName = Some("ETHANOL")
        )
      ): @unchecked
    cargo.cargoSpec.hazardous shouldBe defined

  test("Hazardous で危険物フィールド部分欠落は CargoSpec バリデーション失敗（US05）"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Left(msg) = service
      .book(
        baseCommand.copy(
          cargoType = "Hazardous",
          hazardousClass = Some("3"),
          hazardousUnNumber = None,
          hazardousProperName = Some("ETHANOL")
        )
      ): @unchecked
    msg should include("危険物")

  test("Refrigerated 貨物に温度範囲を渡すと予約成立し refrigeration が反映される（US05）"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service
      .book(
        baseCommand.copy(
          cargoType = "Refrigerated",
          refrigerationMinTemp = Some(-20),
          refrigerationMaxTemp = Some(-5),
          refrigerationUnit = Some("Celsius")
        )
      ): @unchecked
    cargo.cargoSpec.refrigeration shouldBe defined
    cargo.cargoSpec.refrigeration.get.minTemperature shouldBe -20

  test("Refrigerated 貨物で温度範囲未指定は CargoSpec バリデーション失敗"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Left(msg) = service.book(baseCommand.copy(cargoType = "Refrigerated")): @unchecked
    msg should include("冷凍")

  test("assignToRouting: Preliminary 予約を引き渡すと RouteProposed が保存される（US06）"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked

    val Right(next) = service.assignToRouting(cargo.bookingId.value): @unchecked
    next.status shouldBe cargotracker.booking.domain.model.valueobjects.BookingStatus.RouteProposed
    repo.saved.head.status shouldBe cargotracker.booking.domain.model.valueobjects.BookingStatus.RouteProposed

  test("assignToRouting: 存在しない予約 ID はエラー"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    service.assignToRouting("BK-999999") shouldBe Left("予約 BK-999999 が見つかりません")

  test("assignToRouting: フォーマット不正な予約 ID はエラー"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    service.assignToRouting("invalid-id") shouldBe Left("予約 ID の形式が不正です: invalid-id")

  test("Refrigerated 貨物で min > max の温度範囲は不正温度範囲エラー"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Left(msg) = service
      .book(
        baseCommand.copy(
          cargoType = "Refrigerated",
          refrigerationMinTemp = Some(10),
          refrigerationMaxTemp = Some(-10),
          refrigerationUnit = Some("Celsius")
        )
      ): @unchecked
    msg should include("温度範囲が不正")

  // === 境界値・エラー経路網羅（IT3 タスク 0.9 / IT2 review tester 高 #2） ===

  test("book: 到着地の UnLocode 形式不正でエラー"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    service.book(baseCommand.copy(destination = "12345")) shouldBe Left(
      "目的地の UnLocode 形式が不正です"
    )

  test("book: 貨物種別が未知の値でエラー"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    service.book(baseCommand.copy(cargoType = "Unknown")) shouldBe Left("貨物種別が不正です")

  test("book: 重量 0 は Weight バリデーション失敗"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    service.book(baseCommand.copy(weightKg = 0L)) shouldBe Left("重量が不正です")

  test("book: 重量負値は Weight バリデーション失敗"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    service.book(baseCommand.copy(weightKg = -100L)) shouldBe Left("重量が不正です")

  test("book: Hazardous 貨物で 3 フィールド全揃いなら CargoSpec 成立"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service
      .book(
        baseCommand.copy(
          cargoType = "Hazardous",
          hazardousClass = Some("3"),
          hazardousUnNumber = Some("UN1170"),
          hazardousProperName = Some("ETHANOL")
        )
      ): @unchecked
    cargo.cargoSpec.hazardous shouldBe defined
    cargo.cargoSpec.hazardous.get.unNumber shouldBe "UN1170"

  test("book: Refrigerated で同点温度（min == max）も受理"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service
      .book(
        baseCommand.copy(
          cargoType = "Refrigerated",
          refrigerationMinTemp = Some(0),
          refrigerationMaxTemp = Some(0),
          refrigerationUnit = Some("Celsius")
        )
      ): @unchecked
    cargo.cargoSpec.refrigeration.get.minTemperature shouldBe 0
    cargo.cargoSpec.refrigeration.get.maxTemperature shouldBe 0

  test("book: 温度単位が未知の文字列の場合は refrigeration が組み立てられない（General 扱い）"):
    val service = new BookingCommandService(
      new InMemoryCargoRepository,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Left(msg) = service
      .book(
        baseCommand.copy(
          cargoType = "Refrigerated",
          refrigerationMinTemp = Some(-10),
          refrigerationMaxTemp = Some(0),
          refrigerationUnit = Some("Kelvin")
        )
      ): @unchecked
    // 温度単位が解釈不可なら refrigeration = None → Refrigerated 必須違反
    msg should include("冷凍")

  test("book: General 貨物で危険物 / 温度フィールド全 None は正常に成立"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    cargo.cargoSpec.hazardous shouldBe None
    cargo.cargoSpec.refrigeration shouldBe None

  test("assignToRouting: 既に RouteProposed の予約は再度引き渡せない（InvalidStatusTransition）"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    val Right(_) = service.assignToRouting(cargo.bookingId.value): @unchecked

    val Left(msg) = service.assignToRouting(cargo.bookingId.value): @unchecked
    msg should include("RouteProposed")
    msg should include("遷移はできません")

  test("assignItinerary: RouteProposed 予約に経路を紐付けると RouteAssigned で保存される（US11）"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    service.assignToRouting(cargo.bookingId.value)

    val Right(assigned) = service.assignItinerary(cargo.bookingId.value, List("VY-1", "VY-2")): @unchecked
    assigned.status shouldBe cargotracker.booking.domain.model.valueobjects.BookingStatus.RouteAssigned
    assigned.itinerary.map(_.voyageNumbers) shouldBe Some(List("VY-1", "VY-2"))
    repo.findById(cargo.bookingId).get.status shouldBe
      cargotracker.booking.domain.model.valueobjects.BookingStatus.RouteAssigned

  test("assignItinerary: Preliminary からの紐付けは状態遷移違反"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    val Left(msg) = service.assignItinerary(cargo.bookingId.value, List("VY-1")): @unchecked
    msg should include("Preliminary")
    msg should include("RouteAssigned")
    msg should include("遷移はできません")

  test("assignItinerary: 既に RouteAssigned の予約は再紐付け禁止"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    service.assignToRouting(cargo.bookingId.value)
    service.assignItinerary(cargo.bookingId.value, List("VY-1")).isRight shouldBe true

    val Left(msg) = service.assignItinerary(cargo.bookingId.value, List("VY-2")): @unchecked
    msg should include("RouteAssigned")
    msg should include("遷移はできません")

  test("assignItinerary: 存在しない予約 ID はエラー"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    service.assignItinerary("BK-000999", List("VY-1")) shouldBe Left("予約 BK-000999 が見つかりません")

  test("assignItinerary: 空航海リストはエラー"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    service.assignToRouting(cargo.bookingId.value)
    service.assignItinerary(cargo.bookingId.value, Nil) shouldBe Left("経路に含む航海が指定されていません")

  test("confirm: RouteAssigned 予約を確定すると Confirmed に遷移"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    service.assignToRouting(cargo.bookingId.value)
    service.assignItinerary(cargo.bookingId.value, List("VY-1"))

    val Right(confirmed) = service.confirm(cargo.bookingId.value): @unchecked
    confirmed.status shouldBe cargotracker.booking.domain.model.valueobjects.BookingStatus.Confirmed
    repo.findById(cargo.bookingId).get.status shouldBe
      cargotracker.booking.domain.model.valueobjects.BookingStatus.Confirmed

  test("confirm: RouteAssigned 以外の予約は遷移違反"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    val Left(msg) = service.confirm(cargo.bookingId.value): @unchecked
    msg should include("Preliminary")
    msg should include("Confirmed")

  test("reproposeRoute: RouteAssigned 予約を再設計に戻すと itinerary がリセットされる"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    service.assignToRouting(cargo.bookingId.value)
    service.assignItinerary(cargo.bookingId.value, List("VY-1"))

    val Right(reproposed) = service.reproposeRoute(cargo.bookingId.value): @unchecked
    reproposed.status shouldBe cargotracker.booking.domain.model.valueobjects.BookingStatus.RouteProposed
    reproposed.itinerary shouldBe None

  test("reproposeRoute: RouteProposed のままの予約は遷移違反"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    service.assignToRouting(cargo.bookingId.value)
    val Left(msg) = service.reproposeRoute(cargo.bookingId.value): @unchecked
    msg should include("RouteProposed")
    msg should include("遷移はできません")

  test("cancel: Preliminary / RouteProposed / RouteAssigned / Confirmed の 4 状態すべてからキャンセル可能（IT5 H6 デシジョンテーブル網羅）"):
    val startStates = List("Preliminary", "RouteProposed", "RouteAssigned", "Confirmed")
    startStates.foreach { label =>
      val repo = new InMemoryCargoRepository
      val service = new BookingCommandService(
        repo,
        acceptingChecker,
        new InMemoryNotificationLogRepository,
        java.time.Clock.systemUTC()
      )
      val Right(cargo) = service.book(baseCommand): @unchecked
      label match
        case "Preliminary" => ()
        case "RouteProposed" => service.assignToRouting(cargo.bookingId.value).toOption.get
        case "RouteAssigned" =>
          service.assignToRouting(cargo.bookingId.value).toOption.get
          service.assignItinerary(cargo.bookingId.value, List("VY-1")).toOption.get
        case "Confirmed" =>
          service.assignToRouting(cargo.bookingId.value).toOption.get
          service.assignItinerary(cargo.bookingId.value, List("VY-1")).toOption.get
          service.confirm(cargo.bookingId.value).toOption.get

      withClue(s"start=$label: ") {
        val Right(cancelled) = service.cancel(cargo.bookingId.value): @unchecked
        cancelled.status shouldBe cargotracker.booking.domain.model.valueobjects.BookingStatus.Cancelled
      }
    }

  test("cancel: TrackingIssued 以降（IT5 未実装）/ Cancelled は遷移違反（IT5 H6 デシジョンテーブル網羅）"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    service.cancel(cargo.bookingId.value).toOption.get
    // 既に Cancelled の予約への再キャンセル
    val Left(msg) = service.cancel(cargo.bookingId.value): @unchecked
    msg should include("Cancelled")

  test("confirm: 成功時に BookingConfirmed 通知ログが記録される（IT4 タスク 4.4）"):
    val repo = new InMemoryCargoRepository
    val notif = new InMemoryNotificationLogRepository
    val service = new BookingCommandService(repo, acceptingChecker, notif, java.time.Clock.systemUTC())
    val Right(cargo) = service.book(baseCommand): @unchecked
    service.assignToRouting(cargo.bookingId.value)
    service.assignItinerary(cargo.bookingId.value, List("VY-1"))
    service.confirm(cargo.bookingId.value)

    notif.store.map(_.notificationType) should contain(
      cargotracker.booking.domain.model.valueobjects.NotificationType.BookingConfirmed
    )
    // IT5 H5: payload を JSON 構造として検証する（部分文字列マッチではない）
    val confirmedJson = Json.parse(notif.store.last.payload)
    (confirmedJson \ "bookingId").as[String] shouldBe cargo.bookingId.value
    (confirmedJson \ "status").as[String] shouldBe "Confirmed"
    (confirmedJson \ "trackingIssueRequested").as[Boolean] shouldBe true

  test("cancel: 成功時に BookingCancelled 通知ログが記録される（IT4 タスク 4.4）"):
    val repo = new InMemoryCargoRepository
    val notif = new InMemoryNotificationLogRepository
    val service = new BookingCommandService(repo, acceptingChecker, notif, java.time.Clock.systemUTC())
    val Right(cargo) = service.book(baseCommand): @unchecked
    service.cancel(cargo.bookingId.value)
    notif.store.map(_.notificationType) shouldBe Seq(
      cargotracker.booking.domain.model.valueobjects.NotificationType.BookingCancelled
    )
    // IT5 H5: payload を JSON 構造として検証する
    val cancelJson = Json.parse(notif.store.last.payload)
    (cancelJson \ "bookingId").as[String] shouldBe cargo.bookingId.value
    (cancelJson \ "status").as[String] shouldBe "Cancelled"

  test("cancel: 失敗時は通知ログが記録されない"):
    val repo = new InMemoryCargoRepository
    val notif = new InMemoryNotificationLogRepository
    val service = new BookingCommandService(repo, acceptingChecker, notif, java.time.Clock.systemUTC())
    val Right(cargo) = service.book(baseCommand): @unchecked
    service.cancel(cargo.bookingId.value)
    val sizeBefore = notif.store.size
    service.cancel(cargo.bookingId.value) // 既に Cancelled → 失敗
    notif.store.size shouldBe sizeBefore

  test("cancel: 既にキャンセル済みの予約は遷移違反"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(
      repo,
      acceptingChecker,
      new InMemoryNotificationLogRepository,
      java.time.Clock.systemUTC()
    )
    val Right(cargo) = service.book(baseCommand): @unchecked
    service.cancel(cargo.bookingId.value)
    val Left(msg) = service.cancel(cargo.bookingId.value): @unchecked
    msg should include("Cancelled")
    msg should include("遷移はできません")

  test("completeDelivery: TrackingIssued の予約を Delivered に遷移 + DeliveryCompleted 通知 (US16 / IT6)"):
    val repo = new InMemoryCargoRepository
    val notif = new InMemoryNotificationLogRepository
    val service = new BookingCommandService(repo, acceptingChecker, notif, java.time.Clock.systemUTC())
    val Right(cargo) = service.book(baseCommand): @unchecked
    val tracked = Cargo.reconstruct(
      Cargo.Snapshot(
        cargo.bookingId,
        cargo.shipperId,
        cargo.routeSpecification,
        cargo.cargoSpec,
        BookingStatus.TrackingIssued,
        cargo.version,
        cargo.itinerary,
        Some("TN-000001")
      )
    )
    repo.save(tracked)
    val Right(delivered) =
      service.completeDelivery(cargo.bookingId.value, "TN-000001", "USNYC", "署名: 田中"): @unchecked
    delivered.status shouldBe BookingStatus.Delivered
    val log = notif.store
      .find(_.notificationType == cargotracker.booking.domain.model.valueobjects.NotificationType.DeliveryCompleted)
      .get
    val payload = Json.parse(log.payload)
    (payload \ "status").as[String] shouldBe "Delivered"
    (payload \ "recipientConfirmation").as[String] shouldBe "署名: 田中"

  test("logManualStatusUpdate: 既存予約に対し ManualStatusUpdated 通知を記録 (US17 / IT6)"):
    val repo = new InMemoryCargoRepository
    val notif = new InMemoryNotificationLogRepository
    val service = new BookingCommandService(repo, acceptingChecker, notif, java.time.Clock.systemUTC())
    val Right(cargo) = service.book(baseCommand): @unchecked
    val Right(_) =
      service.logManualStatusUpdate(
        cargo.bookingId.value,
        "TN-000007",
        "Loaded",
        "JPTYO",
        "通関手続き完了の連絡を受領"
      ): @unchecked
    val log = notif.store
      .find(_.notificationType == cargotracker.booking.domain.model.valueobjects.NotificationType.ManualStatusUpdated)
      .get
    val payload = Json.parse(log.payload)
    (payload \ "status").as[String] shouldBe "Loaded"
    (payload \ "location").as[String] shouldBe "JPTYO"
    (payload \ "reason").as[String] shouldBe "通関手続き完了の連絡を受領"

  test("completeDelivery: Preliminary からは遷移違反でエラー + 通知ログ未記録 (US16)"):
    val repo = new InMemoryCargoRepository
    val notif = new InMemoryNotificationLogRepository
    val service = new BookingCommandService(repo, acceptingChecker, notif, java.time.Clock.systemUTC())
    val Right(cargo) = service.book(baseCommand): @unchecked
    val Left(msg) =
      service.completeDelivery(cargo.bookingId.value, "TN-000001", "USNYC", "署名"): @unchecked
    msg should include("Delivered")
    notif.store.exists(
      _.notificationType == cargotracker.booking.domain.model.valueobjects.NotificationType.DeliveryCompleted
    ) shouldBe false
