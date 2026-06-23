package cargotracker.booking.domain.model.valueobjects

import cargotracker.shared.domain.Location
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ItinerarySpec extends AnyFunSuite with Matchers:

  private val tyo = Location.unsafeFrom("JPTYO")
  private val nyc = Location.unsafeFrom("USNYC")
  private val lax = Location.unsafeFrom("USLAX")

  test("apply: 1 航海以上で生成できる"):
    Itinerary(List("VY-001")).map(_.voyageNumbers) shouldBe Right(List("VY-001"))

  test("apply: 空リストは EmptyVoyages"):
    Itinerary(Nil) shouldBe Left(Itinerary.EmptyVoyages)

  test("航海番号の順序を保持する"):
    val Right(it) = Itinerary(List("VY-A", "VY-B", "VY-C")): @unchecked
    it.voyageNumbers shouldBe List("VY-A", "VY-B", "VY-C")

  test("apply: voyageNumbers のみから組み立てると legs は空（後方互換）"):
    val Right(it) = Itinerary(List("VY-1", "VY-2")): @unchecked
    it.legs shouldBe empty
    it.isOnRoute("JPTYO") shouldBe false

  test("fromLegs: legs から組み立てると voyageNumbers が leg 順序で導出される (IT7 0.14)"):
    val legs = List(
      ItineraryLeg("VY-1", tyo, lax),
      ItineraryLeg("VY-2", lax, nyc)
    )
    val Right(it) = Itinerary.fromLegs(legs): @unchecked
    it.voyageNumbers shouldBe List("VY-1", "VY-2")
    it.legs shouldBe legs

  test("isOnRoute: legs 上の UN/LOCODE は経路上、それ以外は経路逸脱 (IT7 0.14)"):
    val legs = List(
      ItineraryLeg("VY-1", tyo, lax),
      ItineraryLeg("VY-2", lax, nyc)
    )
    val Right(it) = Itinerary.fromLegs(legs): @unchecked
    it.isOnRoute("JPTYO") shouldBe true
    it.isOnRoute("USLAX") shouldBe true
    it.isOnRoute("USNYC") shouldBe true
    it.isOnRoute("DEHAM") shouldBe false

  test("expectedLocations: 全 leg の from/to の和集合を返す"):
    val legs = List(
      ItineraryLeg("VY-1", tyo, lax),
      ItineraryLeg("VY-2", lax, nyc)
    )
    val Right(it) = Itinerary.fromLegs(legs): @unchecked
    it.expectedLocations shouldBe Set("JPTYO", "USLAX", "USNYC")

  test("fromLegs: 空 list は EmptyVoyages"):
    Itinerary.fromLegs(Nil) shouldBe Left(Itinerary.EmptyVoyages)
