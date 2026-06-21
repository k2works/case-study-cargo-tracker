package cargotracker.booking.application.commandservices

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus}
import cargotracker.shared.domain.ShipperId
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable

class BookingCommandServiceSpec extends AnyFunSuite with Matchers:

  private class InMemoryCargoRepository extends CargoRepository:
    private val store: mutable.Map[BookingId, Cargo] = mutable.Map.empty
    private val seq: AtomicInteger = AtomicInteger(0)
    override def findById(id: BookingId): Option[Cargo] = store.get(id)
    override def findAll(): Seq[Cargo] = store.values.toSeq
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
    val service = new BookingCommandService(repo, acceptingChecker)

    val Right(cargo) = service.book(baseCommand): @unchecked

    cargo.status shouldBe BookingStatus.Preliminary
    cargo.bookingId.value shouldBe "BK-000001"
    repo.saved should have size 1

  test("出発地と目的地が同じならエラー"):
    val service = new BookingCommandService(new InMemoryCargoRepository, acceptingChecker)
    val Left(msg) = service.book(baseCommand.copy(destination = baseCommand.origin)): @unchecked
    msg should include("出発地と目的地が同じ")

  test("不正な UnLocode はエラー"):
    val service = new BookingCommandService(new InMemoryCargoRepository, acceptingChecker)
    service.book(baseCommand.copy(origin = "xx")) shouldBe Left("出発地の UnLocode 形式が不正です")

  test("存在しない荷主は ShipperExistenceChecker で弾かれ永続化されない"):
    val repo = new InMemoryCargoRepository
    val service = new BookingCommandService(repo, rejectingChecker)

    val Left(msg) = service.book(baseCommand): @unchecked
    msg should include("荷主 SH-000001 が見つかりません")
    repo.saved shouldBe empty

  test("危険物 3 フィールド全揃いで HazardousDeclaration が反映される"):
    val service = new BookingCommandService(new InMemoryCargoRepository, acceptingChecker)
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

  test("危険物フィールドが部分欠落の場合は HazardousDeclaration が組み立てられない"):
    val service = new BookingCommandService(new InMemoryCargoRepository, acceptingChecker)
    val Right(cargo) = service
      .book(
        baseCommand.copy(
          cargoType = "Hazardous",
          hazardousClass = Some("3"),
          hazardousUnNumber = None,
          hazardousProperName = Some("ETHANOL")
        )
      ): @unchecked
    cargo.cargoSpec.hazardous shouldBe None
