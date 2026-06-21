import cargotracker.auth.domain.model.repositories.UserRepository
import cargotracker.auth.infrastructure.repositories.ScalikeJdbcUserRepository
import cargotracker.auth.infrastructure.services.AdminUserSeeder
import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.infrastructure.repositories.ScalikeJdbcCargoRepository
import cargotracker.booking.infrastructure.services.ShipperRepositoryBackedExistenceChecker
import cargotracker.estimation.domain.model.repositories.EstimateRepository
import cargotracker.estimation.infrastructure.repositories.ScalikeJdbcEstimateRepository
import cargotracker.shared.domain.pricing.{InMemoryPricingService, PricingService}
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.shipper.infrastructure.repositories.ScalikeJdbcShipperRepository
import com.google.inject.AbstractModule
import play.api.db.DBApi
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}

import java.time.Clock
import javax.inject.{Inject, Singleton}

/** Play（HikariCP）のデータソースを ScalikeJDBC のコネクションプールとして登録する。 */
@Singleton
class ScalikeJdbcInitializer @Inject() (dbApi: DBApi):
  ConnectionPool.singleton(
    new DataSourceConnectionPool(dbApi.database("default").dataSource)
  )

class Module extends AbstractModule:
  override def configure(): Unit =
    bind(classOf[ScalikeJdbcInitializer]).asEagerSingleton()
    bind(classOf[UserRepository]).to(classOf[ScalikeJdbcUserRepository])
    bind(classOf[ShipperRepository]).to(classOf[ScalikeJdbcShipperRepository])
    bind(classOf[EstimateRepository]).to(classOf[ScalikeJdbcEstimateRepository])
    bind(classOf[PricingService]).to(classOf[InMemoryPricingService])
    bind(classOf[CargoRepository]).to(classOf[ScalikeJdbcCargoRepository])
    bind(classOf[ShipperExistenceChecker])
      .to(classOf[ShipperRepositoryBackedExistenceChecker])
    bind(classOf[Clock]).toInstance(Clock.systemUTC())
    // 開発用 admin ユーザーの起動時シード
    bind(classOf[AdminUserSeeder]).asEagerSingleton()
