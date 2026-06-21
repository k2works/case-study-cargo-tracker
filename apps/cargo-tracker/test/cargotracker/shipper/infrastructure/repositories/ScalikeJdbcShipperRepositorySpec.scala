package cargotracker.shipper.infrastructure.repositories

import cargotracker.shared.domain.{ShipperId, ShipperType}
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.valueobjects.DiscountRate
import cargotracker.support.PostgresContainerSupport
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
class ScalikeJdbcShipperRepositorySpec extends AnyFunSuite with Matchers with PostgresContainerSupport:

  test("個人荷主を保存して shipper_code で取得できる"):
    withContainers { container =>
      val app = buildApp(container)
      val repo = new ScalikeJdbcShipperRepository
      val id = repo.nextIdentity()
      val s = Shipper
        .individual(id, "山田太郎", "yamada@example.com", "03-1", "addr")
        .toOption
        .get

      repo.save(s)
      val found = repo.findById(id).get
      found.name shouldBe "山田太郎"
      found.shipperType shouldBe ShipperType.Individual
      app.stop()
    }

  test("法人荷主を保存・取得すると割引率と契約番号が復元される"):
    withContainers { container =>
      val app = buildApp(container)
      val repo = new ScalikeJdbcShipperRepository
      val id = repo.nextIdentity()
      val rate = DiscountRate(0.15).toOption.get
      val s = Shipper
        .corporate(
          id,
          "株式会社 ABC",
          "abc@example.com",
          "03-2",
          "addr",
          "CT-0001",
          rate
        )
        .toOption
        .get

      repo.save(s)
      val found = repo.findById(id).get
      found.shipperType shouldBe ShipperType.Corporate
      found.contractNumber shouldBe Some("CT-0001")
      found.discountRate.value shouldBe 0.15
      app.stop()
    }

  test("メール重複は findByEmail で検出できる"):
    withContainers { container =>
      val app = buildApp(container)
      val repo = new ScalikeJdbcShipperRepository
      val id = repo.nextIdentity()
      val s = Shipper
        .individual(id, "山田", "dup@example.com", "0", "addr")
        .toOption
        .get
      repo.save(s)

      repo.findByEmail("dup@example.com").isDefined shouldBe true
      repo.findByEmail("nope@example.com").isDefined shouldBe false
      app.stop()
    }

  test("nextIdentity は順番に連番を発行する"):
    withContainers { container =>
      val app = buildApp(container)
      val repo = new ScalikeJdbcShipperRepository

      val first = repo.nextIdentity()
      first.value should fullyMatch regex "SH-[0-9]{6}"
      val s1 = Shipper
        .individual(first, "X", "x-seq@example.com", "0", "addr")
        .toOption
        .get
      repo.save(s1)

      val second = repo.nextIdentity()
      val firstNum = first.value.substring(3).toInt
      val secondNum = second.value.substring(3).toInt
      secondNum shouldBe firstNum + 1
      app.stop()
    }
