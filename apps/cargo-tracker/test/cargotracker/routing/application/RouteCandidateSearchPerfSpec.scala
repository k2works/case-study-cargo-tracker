package cargotracker.routing.application

import cargotracker.routing.domain.model.valueobjects.{RoutingLeg, VoyageNumber}
import cargotracker.shared.domain.Location
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.util.Random

/** RouteCandidateSearch のパフォーマンステスト（IT3 タスク 2.8 / US08 非機能要件）。
  *
  * 1,000 航海規模に対する経路探索を 20 回試行し、P95 が 3 秒未満であることを検証する。 隣接リスト化（タスク 2.2）の効果を回帰検知する目的を兼ねる。
  */
class RouteCandidateSearchPerfSpec extends AnyFunSuite with Matchers:

  private val rng = new Random(seed = 42L)

  private def randomLocation(): Location =
    val cc = ('A' + rng.nextInt(26)).toChar.toString + ('A' + rng.nextInt(26)).toChar.toString
    val code = ('A' + rng.nextInt(26)).toChar.toString * 3
    Location.unsafeFrom(cc + code)

  private def genLegs(n: Int, hubs: IndexedSeq[Location]): List[RoutingLeg] =
    val baseInstant = Instant.parse("2099-07-01T00:00:00Z")
    (1 to n).map { i =>
      val from = hubs(rng.nextInt(hubs.size))
      var to = hubs(rng.nextInt(hubs.size))
      while to == from do to = hubs(rng.nextInt(hubs.size))
      val dep = baseInstant.plusSeconds(rng.nextLong(3600L * 24 * 30))
      RoutingLeg(
        VoyageNumber.unsafeFrom(s"VY-${"%04d".format(i)}"),
        from,
        to,
        dep,
        dep.plusSeconds(3600L * (12 + rng.nextLong(72)))
      )
    }.toList

  test("航海 1000 件規模の探索を 20 回行い P95 < 3 秒"):
    val hubs = (1 to 20).map(_ => randomLocation()).distinct.toIndexedSeq
    val legs = genLegs(1000, hubs)
    val origin = hubs.head
    val destination = hubs.last

    // ウォームアップ（JIT 安定化）
    (1 to 3).foreach { _ =>
      RouteCandidateSearch.search(legs, origin, destination, maxLegs = 3)
    }

    val samples = (1 to 20).map { _ =>
      val t0 = System.nanoTime()
      RouteCandidateSearch.search(legs, origin, destination, maxLegs = 3)
      (System.nanoTime() - t0) / 1_000_000L // ms
    }.sorted

    val p95Index = math.ceil(samples.size * 0.95).toInt - 1
    val p95Ms = samples(p95Index)
    info(s"samples (ms): ${samples.mkString(", ")}  p95=$p95Ms ms")
    p95Ms shouldBe <(3000L)
