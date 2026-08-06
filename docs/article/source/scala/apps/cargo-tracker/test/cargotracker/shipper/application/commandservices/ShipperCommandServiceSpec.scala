package cargotracker.shipper.application.commandservices

import cargotracker.shared.domain.ShipperId
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.shipper.domain.model.valueobjects.DiscountRate
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable

class ShipperCommandServiceSpec extends AnyFunSuite with Matchers:

  private class InMemoryShipperRepository extends ShipperRepository:
    private val store: mutable.Map[ShipperId, Shipper] = mutable.Map.empty
    private val seq: AtomicInteger = AtomicInteger(0)
    override def findById(id: ShipperId): Option[Shipper] = store.get(id)
    override def findByEmail(email: String): Option[Shipper] =
      store.values.find(_.email == email)
    override def findAll(): Seq[Shipper] = store.values.toSeq
    override def save(s: Shipper): Unit = store.update(s.shipperId, s)
    override def nextIdentity(): ShipperId =
      ShipperId.unsafeFrom(f"SH-${seq.incrementAndGet()}%06d")
    def saved: Seq[Shipper] = store.values.toSeq

  test("個人荷主を登録すると採番された ShipperId が割り当てられ永続化される"):
    val repo = new InMemoryShipperRepository
    val service = new ShipperCommandService(repo)

    val Right(saved) = service
      .register(
        RegisterShipperCommand.Individual("山田太郎", "yamada@example.com", "03-1111-1111", "東京")
      ): @unchecked

    saved.shipperId.value shouldBe "SH-000001"
    saved.name shouldBe "山田太郎"
    repo.saved should have size 1

  test("法人荷主を登録すると契約番号と割引率が反映される"):
    val repo = new InMemoryShipperRepository
    val service = new ShipperCommandService(repo)

    val Right(saved) = service
      .register(
        RegisterShipperCommand.Corporate(
          "株式会社サンプル",
          "sample@example.com",
          "03-2222-2222",
          "東京",
          "CT-2026-001",
          DiscountRate(0.15).toOption.get
        )
      ): @unchecked

    saved.shipperId.value shouldBe "SH-000001"
    repo.saved should have size 1

  test("ドメインバリデーション失敗時は永続化されずエラーメッセージが返る"):
    val repo = new InMemoryShipperRepository
    val service = new ShipperCommandService(repo)

    val Left(msg) = service
      .register(
        RegisterShipperCommand.Individual("", "yamada@example.com", "03-1111-1111", "東京")
      ): @unchecked

    msg should startWith("荷主の生成に失敗しました")
    repo.saved shouldBe empty

  test("RegisterShipperCommand.from は文字列 shipperType から Individual を組み立てる"):
    val Right(cmd) = RegisterShipperCommand.from(
      "Individual",
      "name",
      "n@example.com",
      "03",
      "addr",
      None,
      None
    ): @unchecked
    cmd shouldBe a[RegisterShipperCommand.Individual]

  test("RegisterShipperCommand.from は Corporate に discountRate を反映する（未指定は 0%）"):
    val Right(cmd) = RegisterShipperCommand.from(
      "Corporate",
      "name",
      "c@example.com",
      "03",
      "addr",
      Some("CT"),
      None
    ): @unchecked
    cmd match
      case RegisterShipperCommand.Corporate(_, _, _, _, contractNumber, rate) =>
        contractNumber shouldBe "CT"
        rate.value shouldBe 0.0
      case _ => fail("Corporate ではない")

  test("RegisterShipperCommand.from は不正な shipperType でエラーを返す"):
    RegisterShipperCommand.from("Unknown", "n", "e@example.com", "p", "a", None, None) shouldBe
      Left("荷主種別が不正です")
