package cargotracker.routing.application

import cargotracker.routing.domain.model.valueobjects.{RoutingLeg, VoyageNumber}
import cargotracker.shared.domain.Location
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class RouteCandidateSearchSpec extends AnyFunSuite with Matchers:

  private val tyo = Location.unsafeFrom("JPTYO")
  private val yok = Location.unsafeFrom("JPYOK")
  private val lax = Location.unsafeFrom("USLAX")
  private val nyc = Location.unsafeFrom("USNYC")
  private val sha = Location.unsafeFrom("CNSHA")

  private val vn1 = VoyageNumber.unsafeFrom("VY-001")
  private val vn2 = VoyageNumber.unsafeFrom("VY-002")
  private val vn3 = VoyageNumber.unsafeFrom("VY-003")

  private def leg(
      vn: VoyageNumber,
      from: Location,
      to: Location,
      dep: String,
      arr: String
  ): RoutingLeg =
    RoutingLeg(vn, from, to, Instant.parse(dep + "Z"), Instant.parse(arr + "Z"))

  test("直行便: 1 区間で目的地到達なら 1 候補"):
    val legs = List(leg(vn1, tyo, lax, "2026-07-01T10:00:00", "2026-07-10T18:00:00"))
    val routes = RouteCandidateSearch.search(legs, tyo, lax)
    routes.size shouldBe 1
    routes.head.origin shouldBe tyo
    routes.head.destination shouldBe lax
    routes.head.transitDays shouldBe 9

  test("中継便: 連結条件を満たす 2 区間ルートを発見"):
    val legs = List(
      leg(vn1, tyo, yok, "2026-07-01T10:00:00", "2026-07-01T18:00:00"),
      leg(vn2, yok, lax, "2026-07-02T08:00:00", "2026-07-12T20:00:00")
    )
    val routes = RouteCandidateSearch.search(legs, tyo, lax)
    routes.size shouldBe 1
    routes.head.legs.map(_.voyageNumber.value) shouldBe List("VY-001", "VY-002")

  test("接続不可: 時刻逆転している中継経路は除外される"):
    val legs = List(
      leg(vn1, tyo, yok, "2026-07-05T10:00:00", "2026-07-05T18:00:00"),
      leg(vn2, yok, lax, "2026-07-01T08:00:00", "2026-07-10T20:00:00")
    )
    RouteCandidateSearch.search(legs, tyo, lax) shouldBe empty

  test("複数経路: 直行と中継の両方を列挙"):
    val legs = List(
      leg(vn1, tyo, lax, "2026-07-01T10:00:00", "2026-07-10T18:00:00"),
      leg(vn2, tyo, yok, "2026-07-01T10:00:00", "2026-07-01T18:00:00"),
      leg(vn3, yok, lax, "2026-07-02T08:00:00", "2026-07-12T20:00:00")
    )
    val routes = RouteCandidateSearch.search(legs, tyo, lax)
    routes.size shouldBe 2

  test("サイクル禁止: 同一地点の再訪を含む経路は探索しない"):
    val legs = List(
      leg(vn1, tyo, yok, "2026-07-01T10:00:00", "2026-07-01T18:00:00"),
      leg(vn2, yok, tyo, "2026-07-02T08:00:00", "2026-07-02T20:00:00"),
      leg(vn3, yok, lax, "2026-07-03T08:00:00", "2026-07-13T20:00:00")
    )
    val routes = RouteCandidateSearch.search(legs, tyo, lax)
    routes.size shouldBe 1
    routes.head.legs.map(_.from) shouldBe List(tyo, yok)

  test("深さ制限: maxLegs を超える経路は除外される"):
    val legs = List(
      leg(vn1, tyo, yok, "2026-07-01T10:00:00", "2026-07-01T18:00:00"),
      leg(vn2, yok, sha, "2026-07-02T08:00:00", "2026-07-03T20:00:00"),
      leg(vn3, sha, lax, "2026-07-04T08:00:00", "2026-07-14T20:00:00")
    )
    RouteCandidateSearch.search(legs, tyo, lax, maxLegs = 2) shouldBe empty
    RouteCandidateSearch.search(legs, tyo, lax, maxLegs = 3).size shouldBe 1

  test("到達不能: 目的地への辺がない場合は空"):
    val legs = List(leg(vn1, tyo, yok, "2026-07-01T10:00:00", "2026-07-01T18:00:00"))
    RouteCandidateSearch.search(legs, tyo, nyc) shouldBe empty
