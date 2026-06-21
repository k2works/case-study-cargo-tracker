package cargotracker.booking.domain.model.valueobjects

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ItinerarySpec extends AnyFunSuite with Matchers:

  test("apply: 1 航海以上で生成できる"):
    Itinerary(List("VY-001")).map(_.voyageNumbers) shouldBe Right(List("VY-001"))

  test("apply: 空リストは EmptyVoyages"):
    Itinerary(Nil) shouldBe Left(Itinerary.EmptyVoyages)

  test("航海番号の順序を保持する"):
    val Right(it) = Itinerary(List("VY-A", "VY-B", "VY-C")): @unchecked
    it.voyageNumbers shouldBe List("VY-A", "VY-B", "VY-C")
