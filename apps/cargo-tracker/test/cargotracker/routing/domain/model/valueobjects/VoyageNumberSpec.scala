package cargotracker.routing.domain.model.valueobjects

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class VoyageNumberSpec extends AnyFunSuite with Matchers:

  test("正常な航海番号フォーマットを受理する"):
    VoyageNumber("VY-001").map(_.value) shouldBe Right("VY-001")
    VoyageNumber("MAEU-12345").map(_.value) shouldBe Right("MAEU-12345")

  test("先頭が英大文字でないと InvalidFormat"):
    VoyageNumber("vy-001") shouldBe Left(VoyageNumber.InvalidFormat)
    VoyageNumber("1VY-001") shouldBe Left(VoyageNumber.InvalidFormat)

  test("空文字 / null は EmptyValue"):
    VoyageNumber("") shouldBe Left(VoyageNumber.EmptyValue)
    VoyageNumber(null) shouldBe Left(VoyageNumber.EmptyValue)

  test("20 文字超過は InvalidFormat"):
    VoyageNumber("V" + "X" * 20) shouldBe Left(VoyageNumber.InvalidFormat)
