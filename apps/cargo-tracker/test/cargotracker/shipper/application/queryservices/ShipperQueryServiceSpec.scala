package cargotracker.shipper.application.queryservices

import cargotracker.shared.domain.ShipperId
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

class ShipperQueryServiceSpec extends AnyFunSuite with Matchers:

  private val ind = Shipper
    .individual(
      ShipperId.unsafeFrom("SH-000001"),
      "山田",
      "yamada@example.com",
      "03-1",
      "東京"
    )
    .toOption
    .get

  private class InMemoryShipperRepository(seed: Shipper*) extends ShipperRepository:
    private val store = mutable.Map.from(seed.map(s => s.shipperId -> s))
    override def findById(id: ShipperId): Option[Shipper] = store.get(id)
    override def findByEmail(email: String): Option[Shipper] =
      store.values.find(_.email == email)
    override def findAll(): Seq[Shipper] = store.values.toSeq
    override def save(s: Shipper): Unit = store.update(s.shipperId, s)
    override def nextIdentity(): ShipperId = ShipperId.unsafeFrom("SH-999999")

  test("findAll はリポジトリの全件を委譲して返す"):
    val service = new ShipperQueryService(new InMemoryShipperRepository(ind))
    service.findAll() should contain only ind

  test("findByEmail は一致する荷主を返す"):
    val service = new ShipperQueryService(new InMemoryShipperRepository(ind))
    service.findByEmail("yamada@example.com") shouldBe Some(ind)
    service.findByEmail("none@example.com") shouldBe None
