import cargotracker.auth.domain.model.repositories.UserRepository
import cargotracker.auth.infrastructure.repositories.ScalikeJdbcUserRepository
import cargotracker.auth.infrastructure.services.AdminUserSeeder
import cargotracker.billing.domain.model.ports.MailNotificationPort
import cargotracker.billing.domain.model.repositories.{BillingCargoQueryPort, InvoiceRepository}
import cargotracker.billing.infrastructure.acl.BookingCargoQueryAdapter
import cargotracker.billing.infrastructure.mail.LoggingMailNotificationAdapter
import cargotracker.billing.infrastructure.repositories.ScalikeJdbcInvoiceRepository
import cargotracker.billing.infrastructure.scheduler.OverdueDetectionScheduler
import cargotracker.booking.application.api.BookingPublicApi
import cargotracker.booking.application.commandservices.BookingCommandService
import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.repositories.{CargoRepository, NotificationLogRepository}
import cargotracker.booking.infrastructure.repositories.{
  ScalikeJdbcCargoRepository,
  ScalikeJdbcNotificationLogRepository
}
import cargotracker.booking.infrastructure.services.ShipperRepositoryBackedExistenceChecker
import cargotracker.estimation.domain.model.repositories.EstimateRepository
import cargotracker.estimation.infrastructure.repositories.ScalikeJdbcEstimateRepository
import cargotracker.handling.domain.model.ports.{BookingNotificationPort, HandlingCargoQueryPort, TrackingLookupPort}
import cargotracker.handling.domain.model.repositories.HandlingActivityRepository
import cargotracker.handling.infrastructure.acl.{BookingAdapter, BookingCargoForHandlingAdapter, TrackingAdapter}
import cargotracker.handling.infrastructure.repositories.ScalikeJdbcHandlingActivityRepository
import cargotracker.routing.domain.model.repositories.{RouteCandidateSelectionRepository, VoyageRepository}
import cargotracker.routing.infrastructure.repositories.{
  ScalikeJdbcRouteCandidateSelectionRepository,
  ScalikeJdbcVoyageRepository
}
import cargotracker.shared.application.{ScalikeJdbcTransactionBoundary, TransactionBoundary}
import cargotracker.shared.domain.pricing.{InMemoryPricingService, PricingService}
import cargotracker.shipper.domain.model.repositories.ShipperRepository
import cargotracker.shipper.infrastructure.repositories.ScalikeJdbcShipperRepository
import cargotracker.tracking.domain.model.repositories.TrackingActivityRepository
import cargotracker.tracking.infrastructure.repositories.ScalikeJdbcTrackingActivityRepository
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
    bind(classOf[NotificationLogRepository]).to(classOf[ScalikeJdbcNotificationLogRepository])
    bind(classOf[VoyageRepository]).to(classOf[ScalikeJdbcVoyageRepository])
    bind(classOf[RouteCandidateSelectionRepository])
      .to(classOf[ScalikeJdbcRouteCandidateSelectionRepository])
    bind(classOf[TrackingActivityRepository])
      .to(classOf[ScalikeJdbcTrackingActivityRepository])
    bind(classOf[HandlingActivityRepository])
      .to(classOf[ScalikeJdbcHandlingActivityRepository])
    bind(classOf[TrackingLookupPort]).to(classOf[TrackingAdapter])
    bind(classOf[BookingNotificationPort]).to(classOf[BookingAdapter])
    bind(classOf[HandlingCargoQueryPort]).to(classOf[BookingCargoForHandlingAdapter])
    bind(classOf[BookingPublicApi]).to(classOf[BookingCommandService])
    bind(classOf[TransactionBoundary]).to(classOf[ScalikeJdbcTransactionBoundary])
    bind(classOf[InvoiceRepository]).to(classOf[ScalikeJdbcInvoiceRepository])
    bind(classOf[BillingCargoQueryPort]).to(classOf[BookingCargoQueryAdapter])
    bind(classOf[MailNotificationPort]).to(classOf[LoggingMailNotificationAdapter])
    bind(classOf[ShipperExistenceChecker])
      .to(classOf[ShipperRepositoryBackedExistenceChecker])
    bind(classOf[Clock]).toInstance(Clock.systemUTC())
    // 開発用 admin ユーザーの起動時シード
    bind(classOf[AdminUserSeeder]).asEagerSingleton()
    // IT9 0.3: 期限超過 Invoice 検出を Pekko Scheduler で日次起動 (billing.overdue.enabled = true 時のみ)
    bind(classOf[OverdueDetectionScheduler]).asEagerSingleton()
