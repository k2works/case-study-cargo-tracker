package cargotracker.shared.domain

import cargotracker.routing.domain.model.valueobjects.VoyageNumber
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/** ScalaCheck による値オブジェクトの不変条件プロパティテスト（IT3 タスク 0.11）。
  *
  * 個別の magic value 検証では捕捉漏れる「あらゆる有効入力で同じ性質を満たす」 「あらゆる無効入力で適切なエラーを返す」を確認する。
  */
class ValueObjectPropertySpec extends AnyPropSpec with Matchers with ScalaCheckPropertyChecks:

  // === ShipperId プロパティ ===

  property("ShipperId: SH-NNNNNN 形式の有効値は常に受理され value が一致する"):
    val genValid: Gen[String] = Gen.listOfN(6, Gen.numChar).map(cs => s"SH-${cs.mkString}")
    forAll(genValid) { raw =>
      val parsed = ShipperId(raw)
      parsed.isRight shouldBe true
      parsed.toOption.get.value shouldBe raw
    }

  property("ShipperId: 桁数が 6 以外の数字列は InvalidFormat"):
    val genInvalid: Gen[String] = for
      n <- Gen.oneOf(1, 2, 3, 4, 5, 7, 8, 10)
      cs <- Gen.listOfN(n, Gen.numChar)
    yield s"SH-${cs.mkString}"
    forAll(genInvalid) { raw =>
      ShipperId(raw) shouldBe Left(ShipperId.InvalidFormat)
    }

  property("ShipperId: 空文字は EmptyValue"):
    ShipperId("") shouldBe Left(ShipperId.EmptyValue)

  // === Money プロパティ ===

  property("Money: 非負の整数は常に Money を生成し amount/currency が保たれる"):
    val genCurrency: Gen[String] = Gen.oneOf("JPY", "USD", "EUR")
    val genAmount: Gen[Long] = Gen.choose(0L, 1_000_000_000L)
    forAll(genCurrency, genAmount) { (cur, amount) =>
      val money = Money(cur, amount)
      money.isRight shouldBe true
      val m = money.toOption.get
      m.amount shouldBe amount
      m.currency shouldBe cur
    }

  property("Money: 負の amount は NegativeAmount"):
    val genNegative: Gen[Long] = Gen.choose(Long.MinValue + 1, -1L)
    forAll(genNegative) { amount =>
      Money("JPY", amount) shouldBe Left(Money.NegativeAmount)
    }

  // === VoyageNumber プロパティ ===

  property("VoyageNumber: 先頭英大文字 + 英数字/- 計 2..20 文字は常に受理"):
    val genHead: Gen[Char] = Gen.alphaUpperChar
    val genTail: Gen[String] =
      for
        n <- Gen.choose(1, 19)
        cs <- Gen.listOfN(n, Gen.oneOf(Gen.alphaUpperChar, Gen.numChar, Gen.const('-')))
      yield cs.mkString
    forAll(genHead, genTail) { (h, t) =>
      val raw = s"$h$t"
      val parsed = VoyageNumber(raw)
      parsed.isRight shouldBe true
      parsed.toOption.get.value shouldBe raw
    }

  property("VoyageNumber: 先頭小文字は InvalidFormat"):
    val genLower: Gen[String] = for
      h <- Gen.alphaLowerChar
      n <- Gen.choose(1, 5)
      cs <- Gen.listOfN(n, Gen.alphaUpperChar)
    yield s"$h${cs.mkString}"
    forAll(genLower) { raw =>
      VoyageNumber(raw) shouldBe Left(VoyageNumber.InvalidFormat)
    }
