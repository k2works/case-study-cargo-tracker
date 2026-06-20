package cargotracker.shared.domain

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MoneySpec extends AnyFunSuite with Matchers:

  test("JPY 金額を整数の最小通貨単位で保持する"):
    val m = Money.jpy(150_000).toOption.get
    m.amount shouldBe 150_000L
    m.currency shouldBe "JPY"

  test("負の金額は拒否される"):
    Money.jpy(-1) shouldBe Left(Money.NegativeAmount)

  test("通貨が同じ金額同士は加算できる"):
    val a = Money.jpy(100).toOption.get
    val b = Money.jpy(250).toOption.get
    (a + b).toOption.get.amount shouldBe 350

  test("異なる通貨同士の加算は CurrencyMismatch を返す"):
    val jpy = Money.jpy(100).toOption.get
    val usd = Money("USD", 100).toOption.get
    (jpy + usd) shouldBe Left(Money.CurrencyMismatch)

  test("数量倍ができる"):
    val m = Money.jpy(120).toOption.get
    m.times(5).amount shouldBe 600
