package cargotracker.shipper.infrastructure.repositories

import cargotracker.shared.domain.ShipperId
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.support.PostgresContainerSupport
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scalikejdbc.*

/** V5 マイグレーション（IT2 タスク 0.11）の動作確認。
  *
  *   - shipper テーブルに version カラムが存在し、INSERT 直後は 0
  *   - 同一 shipperId への 2 度目の save（UPDATE）で version が +1 される
  */
class ShipperVersionColumnSpec extends AnyFunSuite with Matchers with PostgresContainerSupport:

  test("V5 マイグレーション: shipper.version は DEFAULT 0、UPDATE で +1 される"):
    withContainers { container =>
      val app = buildApp(container)
      val repo = new ScalikeJdbcShipperRepository

      val shipperId = ShipperId.unsafeFrom("SH-VER001")
      val initial = Shipper
        .individual(shipperId, "山田太郎", "ver-yamada@example.com", "03-1", "東京")
        .toOption
        .get
      repo.save(initial)

      val v0 = DB.readOnly { implicit session =>
        sql"SELECT version FROM shipper WHERE shipper_code = ${shipperId.value}"
          .map(_.int("version"))
          .single
          .apply()
      }
      v0 shouldBe Some(0)

      // 同じ shipperId で再 save → UPDATE 経路
      val updated = Shipper
        .individual(shipperId, "山田次郎", "ver-yamada@example.com", "03-2", "東京 2")
        .toOption
        .get
      repo.save(updated)

      val v1 = DB.readOnly { implicit session =>
        sql"SELECT version FROM shipper WHERE shipper_code = ${shipperId.value}"
          .map(_.int("version"))
          .single
          .apply()
      }
      v1 shouldBe Some(1)

      app.stop()
    }
