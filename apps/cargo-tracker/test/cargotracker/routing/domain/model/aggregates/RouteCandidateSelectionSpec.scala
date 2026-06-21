package cargotracker.routing.domain.model.aggregates

import cargotracker.routing.domain.model.valueobjects.{RouteSelectionStatus, VoyageNumber}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RouteCandidateSelectionSpec extends AnyFunSuite with Matchers:

  private val voyages = List(VoyageNumber.unsafeFrom("VY-001"), VoyageNumber.unsafeFrom("VY-002"))

  test("create で Pending / version=0 の集約を生成する"):
    val Right(s) = RouteCandidateSelection.create("BK-001", voyages): @unchecked
    s.bookingId shouldBe "BK-001"
    s.voyages shouldBe voyages
    s.status shouldBe RouteSelectionStatus.Pending
    s.version shouldBe 0

  test("空の航海リストは EmptyVoyages を返す"):
    RouteCandidateSelection.create("BK-001", Nil) shouldBe Left(RouteCandidateSelection.EmptyVoyages)

  test("confirm は Pending → Confirmed に遷移する"):
    val Right(pending) = RouteCandidateSelection.create("BK-001", voyages): @unchecked
    val Right(confirmed) = pending.confirm: @unchecked
    confirmed.status shouldBe RouteSelectionStatus.Confirmed

  test("既に Confirmed の経路再確定は AlreadyConfirmed"):
    val Right(pending) = RouteCandidateSelection.create("BK-001", voyages): @unchecked
    val Right(confirmed) = pending.confirm: @unchecked
    confirmed.confirm shouldBe Left(RouteCandidateSelection.AlreadyConfirmed)

  test("reconstruct で永続化から復元できる"):
    val s = RouteCandidateSelection.reconstruct(
      "BK-001",
      voyages,
      RouteSelectionStatus.Confirmed,
      version = 3
    )
    s.version shouldBe 3
    s.status shouldBe RouteSelectionStatus.Confirmed
