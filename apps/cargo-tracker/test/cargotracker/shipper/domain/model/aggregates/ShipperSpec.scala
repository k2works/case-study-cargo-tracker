package cargotracker.shipper.domain.model.aggregates

import cargotracker.shared.domain.{ShipperId, ShipperType}
import cargotracker.shipper.domain.model.valueobjects.DiscountRate
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
class ShipperSpec extends AnyFunSuite with Matchers:

  private val validId = ShipperId("SH-000001").toOption.get

  test("個人荷主を生成できる"):
    val res = Shipper.individual(
      shipperId = validId,
      name = "山田太郎",
      email = "yamada@example.com",
      phone = "03-1234-5678",
      address = "東京都千代田区..."
    )
    res.isRight shouldBe true
    val shipper = res.toOption.get
    shipper.shipperType shouldBe ShipperType.Individual
    shipper.discountRate shouldBe DiscountRate.zero

  test("法人荷主は契約番号と割引率を持つ"):
    val res = Shipper.corporate(
      shipperId = validId,
      name = "株式会社 ABC",
      email = "sales@abc.co.jp",
      phone = "03-9999-9999",
      address = "東京都港区...",
      contractNumber = "CT-2026-0001",
      discountRate = DiscountRate(0.15).toOption.get
    )
    res.isRight shouldBe true
    val shipper = res.toOption.get
    shipper.shipperType shouldBe ShipperType.Corporate
    shipper.contractNumber shouldBe Some("CT-2026-0001")
    shipper.discountRate.value shouldBe 0.15

  test("名前が空白だけの荷主は拒否される"):
    Shipper.individual(validId, "   ", "u@x.com", "0", "addr") shouldBe Left(
      Shipper.InvalidName
    )

  test("メール形式が不正な荷主は拒否される"):
    Shipper.individual(
      validId,
      "山田",
      "invalid",
      "0",
      "addr"
    ) shouldBe Left(Shipper.InvalidEmail)

  test("DiscountRate は 0〜0.30 の範囲のみ受け付ける"):
    DiscountRate(0.0).map(_.value) shouldBe Right(0.0)
    DiscountRate(0.30).map(_.value) shouldBe Right(0.30)
    DiscountRate(0.31) shouldBe Left(DiscountRate.OutOfRange)
    DiscountRate(-0.01) shouldBe Left(DiscountRate.OutOfRange)

  test("ShipperType の表記が荷主インスタンスから取り出せる"):
    val individual = Shipper
      .individual(validId, "Y", "y@x.com", "0", "addr")
      .toOption
      .get
    individual.shipperType shouldBe ShipperType.Individual
