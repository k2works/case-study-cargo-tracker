import cargotracker.auth.domain.UserRepository
import cargotracker.auth.infrastructure.{AdminUserSeeder, ScalikeJdbcUserRepository}
import cargotracker.booking.domain.{CargoRepository, ShipperExistenceChecker}
import cargotracker.booking.infrastructure.{ScalikeJdbcCargoRepository, ShipperRepositoryBackedExistenceChecker}
import cargotracker.estimation.domain.EstimateRepository
import cargotracker.estimation.infrastructure.ScalikeJdbcEstimateRepository
import cargotracker.shared.domain.pricing.{InMemoryPricingService, PricingService}
import cargotracker.shipper.domain.ShipperRepository
import cargotracker.shipper.infrastructure.ScalikeJdbcShipperRepository
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
