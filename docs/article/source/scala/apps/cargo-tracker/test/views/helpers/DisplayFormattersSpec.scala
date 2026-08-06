package views.helpers

import cargotracker.shared.domain.{Location, Money}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Instant, LocalDate}

class DisplayFormattersSpec extends AnyFunSuite with Matchers:

  test("Money を桁区切り + 通貨コードで整形する"):
    val m = Money.jpy(1234567L).toOption.get
    DisplayFormatters.formatMoney(m) shouldBe "1,234,567 JPY"

  test("Option[Money] が None なら '-' を返す"):
    DisplayFormatters.formatMoneyOpt(None) shouldBe "-"
    DisplayFormatters.formatMoneyOpt(Money.jpy(0L).toOption) shouldBe "0 JPY"

  test("Instant を JST 表示に整形する"):
    val instant = Instant.parse("2099-07-01T00:00:00Z")
    DisplayFormatters.formatInstant(instant) shouldBe "2099-07-01 09:00 JST"

  test("LocalDate を ISO 形式で表示する"):
    DisplayFormatters.formatDate(LocalDate.of(2099, 7, 1)) shouldBe "2099-07-01"

  test("Location を name 付き / 無しで整形する"):
    DisplayFormatters.formatLocation(Location.unsafeFrom("JPTYO", "東京")) shouldBe "JPTYO（東京）"
    DisplayFormatters.formatLocation(Location.unsafeFrom("JPTYO")) shouldBe "JPTYO"
