package cargotracker.routing.application.commandservices

import cargotracker.routing.domain.model.aggregates.RouteCandidateSelection
import cargotracker.routing.domain.model.repositories.RouteCandidateSelectionRepository
import cargotracker.routing.domain.model.valueobjects.RouteSelectionStatus
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

class RoutingCommandServiceSpec extends AnyFunSuite with Matchers:

  private class InMemoryRepo extends RouteCandidateSelectionRepository:
    val store: mutable.Map[String, RouteCandidateSelection] = mutable.Map.empty
    override def findByBookingId(bookingId: String): Option[RouteCandidateSelection] = store.get(bookingId)
    override def save(s: RouteCandidateSelection): Unit = store(s.bookingId) = s

  test("confirmRoute: 初回は Confirmed 状態で保存される"):
    val repo = new InMemoryRepo
    val svc = new RoutingCommandService(repo)
    val Right(s) = svc.confirmRoute(SelectRouteCommand("BK-001", List("VY-1", "VY-2"))): @unchecked
    s.status shouldBe RouteSelectionStatus.Confirmed
    repo.store("BK-001").status shouldBe RouteSelectionStatus.Confirmed

  test("confirmRoute: 既に Confirmed なら 既に確定済みです を返す"):
    val repo = new InMemoryRepo
    val svc = new RoutingCommandService(repo)
    svc.confirmRoute(SelectRouteCommand("BK-001", List("VY-1")))
    svc.confirmRoute(SelectRouteCommand("BK-001", List("VY-1"))) shouldBe Left("この予約の経路は既に確定済みです")

  test("confirmRoute: 航海番号が空なら Left"):
    val repo = new InMemoryRepo
    val svc = new RoutingCommandService(repo)
    svc.confirmRoute(SelectRouteCommand("BK-001", Nil)) shouldBe Left("経路に含む航海が指定されていません")

  test("confirmRoute: 航海番号形式不正は Left"):
    val repo = new InMemoryRepo
    val svc = new RoutingCommandService(repo)
    svc.confirmRoute(SelectRouteCommand("BK-001", List("bad"))) shouldBe Left("航海番号の形式が不正です: bad")
