package cargotracker.booking.domain.model.valueobjects

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** RefrigerationSpec の不変条件テスト（US05）。 */
class RefrigerationSpecSpec extends AnyFunSuite with Matchers:

  test("最低温度 ≤ 最高温度なら生成できる（Celsius）"):
    val Right(spec) =
      RefrigerationSpec(-20, -5, TemperatureUnit.Celsius): @unchecked
    spec.minTemperature shouldBe -20
    spec.maxTemperature shouldBe -5
    spec.unit shouldBe TemperatureUnit.Celsius

  test("最低温度 = 最高温度（一点制御）も許容"):
    val Right(spec) =
      RefrigerationSpec(0, 0, TemperatureUnit.Celsius): @unchecked
    spec.minTemperature shouldBe 0
    spec.maxTemperature shouldBe 0

  test("最低温度 > 最高温度はバリデーション失敗"):
    RefrigerationSpec(5, -5, TemperatureUnit.Celsius) shouldBe Left(
      RefrigerationSpec.InvalidTemperatureRange
    )

  test("華氏単位も同様に検証される"):
    val Right(spec) =
      RefrigerationSpec(32, 40, TemperatureUnit.Fahrenheit): @unchecked
    spec.unit shouldBe TemperatureUnit.Fahrenheit

  test("TemperatureUnit.fromName は大文字小文字を区別しない"):
    TemperatureUnit.fromName("celsius") shouldBe Some(TemperatureUnit.Celsius)
    TemperatureUnit.fromName("FAHRENHEIT") shouldBe Some(TemperatureUnit.Fahrenheit)
    TemperatureUnit.fromName("kelvin") shouldBe None
