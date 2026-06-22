package cargotracker.tracking.domain.model.valueobjects

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class TrackingNumberSpec extends AnyFunSuite with Matchers:

  test("apply: TN- + 6 桁数字を受理する"):
    TrackingNumber("TN-000001").map(_.value) shouldBe Right("TN-000001")
    TrackingNumber("TN-999999").map(_.value) shouldBe Right("TN-999999")

  test("apply: TN- + 7-18 桁数字も受理する（VARCHAR(20) 制約に余裕）"):
    TrackingNumber("TN-1234567").map(_.value) shouldBe Right("TN-1234567")
    TrackingNumber("TN-123456789012345678").map(_.value) shouldBe Right("TN-123456789012345678")

  test("apply: TN- なし / 短すぎる / 文字混在は拒否"):
    TrackingNumber("TN-12345") shouldBe Left(TrackingNumber.InvalidFormat)
    TrackingNumber("000001") shouldBe Left(TrackingNumber.InvalidFormat)
    TrackingNumber("TN-ABCDEF") shouldBe Left(TrackingNumber.InvalidFormat)
    TrackingNumber("") shouldBe Left(TrackingNumber.InvalidFormat)

  test("fromSequence: BIGSERIAL 値から TN-000001 形式に整形する（ADR 0010）"):
    TrackingNumber.fromSequence(1L).value shouldBe "TN-000001"
    TrackingNumber.fromSequence(999999L).value shouldBe "TN-999999"
    TrackingNumber.fromSequence(1000000L).value shouldBe "TN-1000000"

  test("value: opaque type の中身を取り出す"):
    val tn = TrackingNumber.unsafeFrom("TN-000042")
    tn.value shouldBe "TN-000042"
