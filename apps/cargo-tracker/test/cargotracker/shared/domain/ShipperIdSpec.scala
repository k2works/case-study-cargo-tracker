package cargotracker.shared.domain

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ShipperIdSpec extends AnyFunSuite with Matchers:

  test("SH-XXXXXX 形式の文字列から ShipperId を生成できる"):
    val res = ShipperId("SH-000001")
    res.isRight shouldBe true
    res.toOption.get.value shouldBe "SH-000001"

  test("形式不正の文字列は InvalidShipperIdFormat を返す"):
    ShipperId("000001") shouldBe Left(ShipperId.InvalidFormat)
    ShipperId("SH-") shouldBe Left(ShipperId.InvalidFormat)
    ShipperId("sh-000001") shouldBe Left(ShipperId.InvalidFormat)

  test("空文字列は EmptyValue を返す"):
    ShipperId("") shouldBe Left(ShipperId.EmptyValue)

  test("永続化からの読み戻し用 unsafeFrom はバリデーションを省く"):
    val id = ShipperId.unsafeFrom("anything")
    id.value shouldBe "anything"

  test("ShipperType は個人と法人を識別できる"):
    ShipperType.Individual.toString shouldBe "Individual"
    ShipperType.Corporate.toString shouldBe "Corporate"
    ShipperType.fromName("Individual") shouldBe Some(ShipperType.Individual)
    ShipperType.fromName("Corporate") shouldBe Some(ShipperType.Corporate)
    ShipperType.fromName("Unknown") shouldBe None
