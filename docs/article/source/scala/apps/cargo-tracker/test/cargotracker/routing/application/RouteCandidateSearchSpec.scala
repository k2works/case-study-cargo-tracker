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

  // === 貨物種別フィルタ（IT3 タスク 2.4 / US08） ===

  import cargotracker.routing.domain.model.aggregates.Voyage
  import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, Schedule}
  import cargotracker.shared.domain.CargoType

  private def voyage(
      vn: VoyageNumber,
      from: Location,
      to: Location,
      supported: Set[CargoType]
  ): Voyage =
    val schedule = Schedule(
      List(
        CarrierMovement(
          from,
          to,
          Instant.parse("2026-07-01T10:00:00Z"),
          Instant.parse("2026-07-10T18:00:00Z")
        ).toOption.get
      )
    ).toOption.get
    Voyage.register(vn, schedule, "V", "C", supported)

  test("toRoutingLegs: cargoTypeFilter 未指定なら全航海の辺を返す"):
    val voyages = Seq(
      voyage(vn1, tyo, lax, Set(CargoType.General)),
      voyage(vn2, tyo, lax, Set(CargoType.Hazardous))
    )
    RouteCandidateSearch.toRoutingLegs(voyages).size shouldBe 2

  test("toRoutingLegs: cargoTypeFilter 指定時は非対応航海を除外する"):
    val voyages = Seq(
      voyage(vn1, tyo, lax, Set(CargoType.General)),
      voyage(vn2, tyo, lax, Set(CargoType.Hazardous, CargoType.General)),
      voyage(vn3, tyo, lax, Set(CargoType.Refrigerated))
    )
    val legs = RouteCandidateSearch.toRoutingLegs(voyages, Some(CargoType.Hazardous))
    legs.map(_.voyageNumber.value) shouldBe List("VY-002")

  // === 上位 N 候補選定（IT3 タスク 2.5 / US08） ===

  test("topN: 区間数の少ない順（直行優先）→ 所要日数 → 出港時刻で並ぶ"):
    val legs = List(
      // 中継 2 区間、所要 11 日
      leg(vn1, tyo, yok, "2026-07-01T10:00:00", "2026-07-01T18:00:00"),
      leg(vn2, yok, lax, "2026-07-02T08:00:00", "2026-07-12T20:00:00"),
      // 直行 1 区間、所要 9 日
      leg(vn3, tyo, lax, "2026-07-01T10:00:00", "2026-07-10T18:00:00")
    )
    val routes = RouteCandidateSearch.search(legs, tyo, lax)
    val top2 = RouteCandidateSearch.topN(routes, 2)
    top2.head.legs.size shouldBe 1 // 直行を最優先
    top2.head.voyages shouldBe List(vn3)
    top2(1).legs.size shouldBe 2 // 中継

  test("topN: n=0 は IllegalArgumentException"):
    a[IllegalArgumentException] should be thrownBy RouteCandidateSearch.topN(Nil, 0)

  test("topN: 候補が n より少なくても全件返す"):
    val legs = List(leg(vn1, tyo, lax, "2026-07-01T10:00:00", "2026-07-10T18:00:00"))
    val routes = RouteCandidateSearch.search(legs, tyo, lax)
    RouteCandidateSearch.topN(routes, 5).size shouldBe 1
