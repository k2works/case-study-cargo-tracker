package cargotracker.shipper.infrastructure.repositories

import cargotracker.shared.domain.{OptimisticLockException, ShipperId}
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.support.{DbCleanupSupport, PostgresContainerSupport}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Shipper 集約の楽観ロック競合検証（IT3 タスク 0.2）。 */
class ShipperOptimisticLockSpec extends AnyFunSuite with Matchers with PostgresContainerSupport with DbCleanupSupport:

  test("正常系: 期待 version と一致する UPDATE は成功し version+1"):
    val repo = new ScalikeJdbcShipperRepository
    val id = ShipperId.unsafeFrom("SH-LOCK01")
    val s0 = Shipper.individual(id, "山田太郎", "lock1@example.com", "03-1", "東京").toOption.get
    repo.save(s0)
    val loaded = repo.findById(id).get
    loaded.version shouldBe 0

    val updated = Shipper
      .individual(id, "山田次郎", "lock1@example.com", "03-2", "東京 2")
      .toOption
      .get
      .withVersion(0)
    repo.save(updated)
    repo.findById(id).get.version shouldBe 1

  test("競合: 古い version で save すると OptimisticLockException"):
    val repo = new ScalikeJdbcShipperRepository
    val id = ShipperId.unsafeFrom("SH-LOCK02")
    val s0 = Shipper.individual(id, "佐藤", "lock2@example.com", "03-1", "東京").toOption.get
    repo.save(s0)
    // 別セッションが先に更新（DB の version は 1 へ）
    val firstUpdate = Shipper
      .individual(id, "佐藤改名", "lock2@example.com", "03-1", "東京")
      .toOption
      .get
      .withVersion(0)
    repo.save(firstUpdate)

    // version=0 のまま保存しようとすると競合
    val staleUpdate = Shipper
      .individual(id, "別人", "lock2@example.com", "03-9", "東京")
      .toOption
      .get
      .withVersion(0)
    val ex = intercept[OptimisticLockException] {
      repo.save(staleUpdate)
    }
    ex.entityType shouldBe "Shipper"
    ex.identifier shouldBe "SH-LOCK02"
