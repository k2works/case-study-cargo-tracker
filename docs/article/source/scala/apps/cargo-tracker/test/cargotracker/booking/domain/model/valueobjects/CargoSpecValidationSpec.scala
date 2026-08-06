package cargotracker.booking.domain.model.valueobjects

import cargotracker.shared.domain.{CargoType, Weight}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** CargoSpec.create の条件付き必須バリデーション（US05）。 */
class CargoSpecValidationSpec extends AnyFunSuite with Matchers:

  private val weight = Weight(1000).toOption.get
  private val haz = HazardousDeclaration("3", "UN1170", "ETHANOL")
  private val refrig = RefrigerationSpec(-20, -5, TemperatureUnit.Celsius).toOption.get

  test("General は危険物・冷凍情報なしで生成可能"):
    CargoSpec.create(CargoType.General, weight, None, None, None, None).isRight shouldBe true

  test("Hazardous は HazardousDeclaration 必須"):
    CargoSpec.create(CargoType.Hazardous, weight, None, None, None, None) shouldBe
      Left(CargoSpec.HazardousDeclarationRequired)

  test("Hazardous + HazardousDeclaration あれば生成可能"):
    val Right(spec) = CargoSpec
      .create(CargoType.Hazardous, weight, None, None, Some(haz), None): @unchecked
    spec.hazardous shouldBe Some(haz)

  test("Refrigerated は RefrigerationSpec 必須"):
    CargoSpec.create(CargoType.Refrigerated, weight, None, None, None, None) shouldBe
      Left(CargoSpec.RefrigerationSpecRequired)

  test("Refrigerated + RefrigerationSpec あれば生成可能"):
    val Right(spec) = CargoSpec
      .create(CargoType.Refrigerated, weight, None, None, None, Some(refrig)): @unchecked
    spec.refrigeration shouldBe Some(refrig)

  test("General に refrigeration が付いていても許容（任意項目）"):
    CargoSpec
      .create(CargoType.General, weight, None, None, None, Some(refrig))
      .isRight shouldBe true
