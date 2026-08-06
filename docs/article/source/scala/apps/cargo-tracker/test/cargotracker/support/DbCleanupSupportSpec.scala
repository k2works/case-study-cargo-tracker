package cargotracker.support

import cargotracker.shared.domain.ShipperId
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.infrastructure.repositories.ScalikeJdbcShipperRepository
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** DbCleanupSupport の動作確認（IT2 タスク 0.6）。 各テスト開始前にアプリケーション側テーブルが TRUNCATE されることを 2 回のテスト連鎖で検証する。
  *
  * DbCleanupSupport が `afterContainersStart` で ConnectionPool を登録するため、 Play アプリ（buildApp）を起動せずに scalikejdbc を直接利用できる。
  */
class DbCleanupSupportSpec extends AnyFunSuite with Matchers with PostgresContainerSupport with DbCleanupSupport:

  test("最初のテストで荷主を 1 件登録する"):
    val repo = new ScalikeJdbcShipperRepository
    val id = ShipperId.unsafeFrom("SH-CLEAN1")
    val s =
      Shipper.individual(id, "テスト", "clean1@example.com", "03-1", "東京").toOption.get
    repo.save(s)
    repo.findAll().size shouldBe 1

  test("次のテストでは前回登録分が TRUNCATE 済みで 0 件"):
    val repo = new ScalikeJdbcShipperRepository
    repo.findAll() shouldBe empty
